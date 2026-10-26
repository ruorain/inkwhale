package com.ruorain.ideas;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 和模型对话，并且和想法仓库打通：
 *   - 带上下文进入（当前文档 / 周报 / 最近想法标题）
 *   - 发问前先在本地检索相关想法，命中片断注入 prompt
 *   - 每条回答可以「存为想法」
 *   - 聊够 3 轮可以「整理成一篇想法」
 */
public class ChatActivity extends BaseActivity {

    private static final int MATCH = LinearLayout.LayoutParams.MATCH_PARENT;
    private static final int WRAP = LinearLayout.LayoutParams.WRAP_CONTENT;
    private static final int MAX_TURNS = 20;

    private static class Msg {
        final String role;
        final String text;
        final List<Quote> quotes;

        Msg(String role, String text, List<Quote> quotes) {
            this.role = role;
            this.text = text;
            this.quotes = quotes;
        }
    }

    private static class Quote {
        final String path;
        final String title;
        final String snippet;

        Quote(String path, String title, String snippet) {
            this.path = path;
            this.title = title;
            this.snippet = snippet;
        }
    }

    private LinearLayout messages;
    private ScrollView scroll;
    private EditText input;
    private TextView modelLabel;
    private TextView topicLabel;
    private TextView effortLabel;
    private LinearLayout contextChip;
    private TextView summarizeBtn;

    private boolean sending;
    private String contextPath;
    private String contextText;

    private final List<Msg> history = new ArrayList<Msg>();
    private List<GitHubApi.Entry> known = new ArrayList<GitHubApi.Entry>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        contextPath = getIntent().getStringExtra("contextPath");
        contextText = getIntent().getStringExtra("contextText");

        setContentView(build());
        watchKeyboard();
        refreshModelLabel();
        loadKnownFiles();
        if (contextPath != null) loadContext(contextPath);
        if (!restoreChat()) showWelcome();
    }

    private static String today() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(new java.util.Date());
    }

    /** 把当天的对话存到本地，下次进来接着聊 */
    private void persistChat() {
        if (history.isEmpty()) return;
        try {
            org.json.JSONArray arr = new org.json.JSONArray();
            for (Msg m : history) {
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("r", m.role);
                o.put("t", m.text);
                arr.put(o);
            }
            Store.saveCurrentChat(this, arr.toString());
        } catch (Exception ignored) {
            // 存失败不影响聊天
        }
    }

    private boolean restoreChat() {
        String json = Store.loadCurrentChat(this);
        if (json == null || json.isEmpty()) return false;
        try {
            org.json.JSONArray arr = new org.json.JSONArray(json);
            if (arr.length() == 0) return false;
            history.clear();
            messages.removeAllViews();
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.getJSONObject(i);
                String role = o.optString("r", "user");
                String text = o.optString("t", "");
                history.add(new Msg(role, text, null));
                addBubble(text, "user".equals(role), null, null, 0);
            }
            updateSummarizeVisibility();
            scrollToBottom();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private LinearLayout inputWrap;

    /** 键盘弹起/收起都会改变根视图高度：跟着滚到底，并把输入框底部边距压到 6dp */
    private void watchKeyboard() {
        final View root = getWindow().getDecorView();
        if (root == null) return;
        root.getViewTreeObserver().addOnGlobalLayoutListener(
                new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                    private int lastHeight;

                    @Override
                    public void onGlobalLayout() {
                        int h = root.getHeight();
                        if (h == lastHeight) return;
                        boolean shrunk = lastHeight > 0 && h < lastHeight;
                        boolean first = lastHeight == 0;
                        lastHeight = h;

                        if (inputWrap != null) {
                            inputWrap.setPadding(UI.dp(ChatActivity.this, 12),
                                    UI.dp(ChatActivity.this, 8),
                                    UI.dp(ChatActivity.this, 12),
                                    UI.dp(ChatActivity.this, shrunk ? 6 : 14));
                        }
                        if (!first) scrollToBottom();
                    }
                });
    }

    private void scrollToBottom() {
        if (scroll == null) return;
        scroll.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isFinishing()) scroll.fullScroll(View.FOCUS_DOWN);
            }
        }, 60);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshModelLabel();
    }

    // ── 构建 ──────────────────────────────────────────────
    private View build() {
        LinearLayout col = UI.row(this, false);
        col.setBackgroundColor(Colors.bg(dark));
        col.addView(buildBar());

        contextChip = UI.row(this, true);
        contextChip.setGravity(Gravity.CENTER_VERTICAL);
        contextChip.setBackground(UI.roundRect(this, Colors.accentSoft(this, dark), 10));
        contextChip.setPadding(UI.dp(this, 12), UI.dp(this, 8), UI.dp(this, 10), UI.dp(this, 8));
        contextChip.setVisibility(View.GONE);
        LinearLayout.LayoutParams chipLp = UI.lp(MATCH, WRAP);
        chipLp.leftMargin = UI.dp(this, 14);
        chipLp.rightMargin = UI.dp(this, 14);
        chipLp.topMargin = UI.dp(this, 2);
        col.addView(contextChip, chipLp);

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        messages = UI.row(this, false);
        messages.setPadding(UI.dp(this, 14), UI.dp(this, 10), UI.dp(this, 14), UI.dp(this, 16));
        scroll.addView(messages);
        col.addView(scroll, UI.lp(MATCH, 0, 1f));

        summarizeBtn = UI.text(this, "把这段讨论整理成一篇想法 →", 13.5f, Colors.accent(this), Typeface.BOLD);
        summarizeBtn.setGravity(Gravity.CENTER);
        summarizeBtn.setBackground(UI.roundRect(this, Colors.accentSoft(this, dark), 10));
        summarizeBtn.setPadding(0, UI.dp(this, 10), 0, UI.dp(this, 10));
        summarizeBtn.setVisibility(View.GONE);
        summarizeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                summarizeToNote();
            }
        });
        LinearLayout.LayoutParams sumLp = UI.lp(MATCH, WRAP);
        sumLp.leftMargin = UI.dp(this, 14);
        sumLp.rightMargin = UI.dp(this, 14);
        sumLp.bottomMargin = UI.dp(this, 6);
        col.addView(summarizeBtn, sumLp);

        col.addView(buildInput());
        return col;
    }

    private View buildBar() {
        LinearLayout bar = UI.row(this, true);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(UI.dp(this, 8), UI.dp(this, 12), UI.dp(this, 8), UI.dp(this, 8));

        bar.addView(iconButton(R.drawable.ic_arrow_left, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
                overridePendingTransition(R.anim.back_in, R.anim.back_out);
            }
        }));

        LinearLayout mid = UI.row(this, false);
        mid.setGravity(Gravity.START);
        LinearLayout.LayoutParams midLp = UI.lp(MATCH, WRAP, 1f);
        midLp.leftMargin = UI.dp(this, 8);
        bar.addView(mid, midLp);

        topicLabel = UI.text(this, Store.chatTopic(this), 15f, Colors.text(dark), Typeface.BOLD);
        topicLabel.setMaxLines(1);
        topicLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
        mid.addView(topicLabel);
        topicLabel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Sheet.Form f = new Sheet.Form();
                f.title = "话题名字";
                f.label = "这段对话在聊什么";
                f.hint = "比如：云笔记改造、毕设选题";
                f.initial = Store.chatTopic(ChatActivity.this);
                f.onSubmit = new Sheet.OnForm() {
                    @Override
                    public void onSubmit(String value, String extra) {
                        Store.setChatTopic(ChatActivity.this, value);
                        topicLabel.setText(Store.chatTopic(ChatActivity.this));
                    }
                };
                Sheet.showForm(ChatActivity.this, dark, f);
            }
        });

        LinearLayout picks = UI.row(this, true);
        picks.setGravity(Gravity.START);
        modelLabel = chip("", Colors.text2(dark), Colors.surface2(dark));
        effortLabel = chip("", Colors.accent(this), Colors.accentSoft(this, dark));
        LinearLayout.LayoutParams m1 = UI.lp(WRAP, WRAP);
        m1.rightMargin = UI.dp(this, 6);
        modelLabel.setLayoutParams(m1);
        picks.addView(modelLabel);
        picks.addView(effortLabel);
        LinearLayout.LayoutParams plp = UI.lp(WRAP, WRAP);
        plp.topMargin = UI.dp(this, 5);
        mid.addView(picks, plp);

        bar.addView(iconButton(R.drawable.ic_settings, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ChatActivity.this, SettingsActivity.class));
            }
        }));
        bar.addView(iconButton(R.drawable.ic_plus, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Sheet.showOptions(ChatActivity.this, dark, "开始新对话？",
                        "当前这段会从本机清掉，话题名也会重置",
                        new String[]{"开始新对话"},
                        new Sheet.OnPick() {
                            @Override
                            public void onPick(int index) {
                                history.clear();
                                Store.saveCurrentChat(ChatActivity.this, "[]");
                                Store.setChatTopic(ChatActivity.this, "未命名对话");
                                topicLabel.setText(Store.chatTopic(ChatActivity.this));
                                showWelcome();
                                Banner.show(ChatActivity.this, dark, "新对话", false);
                            }
                        });
            }
        }));
        return bar;
    }

    private View buildInput() {
        inputWrap = UI.row(this, true);
        inputWrap.setGravity(Gravity.CENTER_VERTICAL);
        inputWrap.setPadding(UI.dp(this, 12), UI.dp(this, 8), UI.dp(this, 12), UI.dp(this, 14));
        inputWrap.setBackgroundColor(Colors.bg(dark));

        LinearLayout wrap = inputWrap;

        input = new EditText(this);
        input.setHint("问点什么…");
        input.setHintTextColor(Colors.text3(dark));
        input.setTextColor(Colors.text(dark));
        input.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14.5f);
        input.setBackground(UI.roundRect(this, Colors.surface(dark), 20, Colors.line(dark), 0.8f));
        input.setPadding(UI.dp(this, 16), UI.dp(this, 11), UI.dp(this, 16), UI.dp(this, 11));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setMaxLines(4);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    send();
                    return true;
                }
                return false;
            }
        });
        wrap.addView(input, UI.lp(0, WRAP, 1f));

        FrameLayout send = new FrameLayout(this);
        send.setBackground(UI.circle(this, Colors.accent(this)));
        ImageView arrow = UI.icon(this, R.drawable.ic_send, 20, 0xFFFFFFFF);
        send.addView(arrow, new FrameLayout.LayoutParams(UI.dp(this, 20), UI.dp(this, 20), Gravity.CENTER));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(UI.dp(this, 44), UI.dp(this, 44));
        slp.leftMargin = UI.dp(this, 8);
        send.setLayoutParams(slp);
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                send();
            }
        });
        UI.press(send);
        wrap.addView(send);
        return wrap;
    }

    // ── 上下文 ────────────────────────────────────────────
    private void loadKnownFiles() {
        try {
            String json = Store.loadCache(this);
            if (json == null || json.isEmpty()) return;
            JSONArray arr = new JSONArray(json);
            List<GitHubApi.Entry> list = new ArrayList<GitHubApi.Entry>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new GitHubApi.Entry(o.optString("p", ""), o.optLong("s", 0)));
            }
            known = list;
        } catch (Exception ignored) {
            // 没缓存就从空开始
        }
    }

    private void loadContext(final String path) {
        String cached = Store.cachedFile(this, path);
        if (!cached.isEmpty()) {
            contextText = cached;
            showChip(baseName(path), contextText.length());
        }
        String token = Store.token(this);
        if (token.isEmpty()) return;
        GitHubApi.readFile(Store.repo(this), Store.branch(this), path, token,
                new GitHubApi.Cb<GitHubApi.Doc>() {
                    @Override
                    public void ok(GitHubApi.Doc doc) {
                        contextText = doc.text;
                        Store.cacheFile(ChatActivity.this, path, doc.text);
                        showChip(baseName(path), doc.text.length());
                    }

                    @Override
                    public void err(String message) {
                        // 离线时保留缓存版本
                    }
                });
    }

    private void showChip(String label, int chars) {
        contextChip.removeAllViews();
        contextChip.setVisibility(View.VISIBLE);

        contextChip.addView(UI.icon(this, R.drawable.ic_file_text, 14, Colors.accent(this)));
        TextView t = UI.text(this, label, 12.5f, Colors.accent(this));
        t.setMaxLines(1);
        t.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        LinearLayout.LayoutParams tlp = UI.lp(0, WRAP, 1f);
        tlp.leftMargin = UI.dp(this, 7);
        contextChip.addView(t, tlp);

        FrameLayout x = new FrameLayout(this);
        ImageView xi = UI.icon(this, R.drawable.ic_x, 13, Colors.text3(dark));
        x.addView(xi, new FrameLayout.LayoutParams(UI.dp(this, 13), UI.dp(this, 13), Gravity.CENTER));
        x.setLayoutParams(UI.lp(UI.dp(this, 26), UI.dp(this, 26)));
        x.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                contextText = null;
                contextPath = null;
                contextChip.setVisibility(View.GONE);
            }
        });
        contextChip.addView(x);
    }

    private String baseName(String p) {
        return p.substring(p.lastIndexOf('/') + 1);
    }

    // ── 消息 ──────────────────────────────────────────────
    private void showWelcome() {
        messages.removeAllViews();
        AiClient.Model model = AiClient.currentModel(this);
        boolean zp = model.isZhipu();

        LinearLayout box = UI.row(this, false);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(UI.dp(this, 20), UI.dp(this, 130), UI.dp(this, 20), UI.dp(this, 20));

        if (zp) {
            ImageView logo = new ImageView(this);
            logo.setImageResource(R.drawable.zhipu);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            // wordmark 原图是黑色，深色模式下整体反白（只改颜色，保留透明）
            logo.setColorFilter(dark ? 0xFFFFFFFF : 0xFF161616);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    UI.dp(this, 156), UI.dp(this, 33));
            blp.gravity = Gravity.CENTER_HORIZONTAL;
            box.addView(logo, blp);
        } else {
            ImageView whale = new ImageView(this);
            whale.setImageResource(R.drawable.whale);
            whale.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(UI.dp(this, 56), UI.dp(this, 56));
            wlp.gravity = Gravity.CENTER_HORIZONTAL;
            box.addView(whale, wlp);
        }

        TextView t = UI.text(this, zp ? "跟 GLM 说说看" : "想聊点什么？",
                16f, Colors.text2(dark), Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp = UI.lp(WRAP, WRAP);
        tlp.topMargin = UI.dp(this, 14);
        t.setLayoutParams(tlp);
        box.addView(t);

        messages.addView(box);
        updateSummarizeVisibility();
    }

    private void addBubble(final String text, final boolean user, final List<Quote> quotes,
                           String reasoning, long elapsedMs) {
        LinearLayout row = UI.row(this, false);
        row.setGravity(user ? Gravity.END : Gravity.START);

        // 思考过程：默认收起，点开看
        if (!user && reasoning != null && !reasoning.isEmpty()) {
            final int secs = Math.max(1, (int) (elapsedMs / 1000));
            final TextView head = UI.text(this, "▸ 深度思考 " + secs + " 秒", 11.5f, Colors.text3(dark));
            head.setPadding(UI.dp(this, 4), 0, 0, UI.dp(this, 6));
            final TextView body = UI.text(this, reasoning, 11.5f, Colors.text3(dark));
            body.setLineSpacing(UI.dp(this, 3), 1f);
            body.setPadding(UI.dp(this, 12), UI.dp(this, 8), UI.dp(this, 12), UI.dp(this, 8));
            body.setBackground(UI.roundRect(this, Colors.surface2(dark), 10));
            body.setVisibility(View.GONE);
            head.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean show = body.getVisibility() != View.VISIBLE;
                    body.setVisibility(show ? View.VISIBLE : View.GONE);
                    head.setText((show ? "▾ " : "▸ ") + "深度思考 " + secs + " 秒");
                }
            });
            row.addView(head, UI.lp(WRAP, WRAP));
            row.addView(body, UI.lp(MATCH, WRAP));
        }

        TextView bubble = UI.text(this, text, 14f, user ? 0xFFFFFFFF : Colors.text(dark));
        bubble.setLineSpacing(UI.dp(this, 3), 1f);
        bubble.setTextIsSelectable(true);
        bubble.setPadding(UI.dp(this, 14), UI.dp(this, 10), UI.dp(this, 14), UI.dp(this, 10));
        bubble.setBackground(UI.roundRect(this, user ? Colors.accent(this) : Colors.surface(dark), 14,
                user ? 0x00000000 : Colors.line(dark), 0.8f));
        bubble.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.78f));
        row.addView(bubble, UI.lp(WRAP, WRAP));

        if (!user) {
            if (quotes != null && !quotes.isEmpty()) {
                LinearLayout quoteBox = UI.row(this, false);
                quoteBox.setPadding(UI.dp(this, 4), UI.dp(this, 8), 0, 0);
                TextView q = UI.text(this, "引用了 " + quotes.size() + " 篇想法 ▾", 11.5f, Colors.text3(dark));
                quoteBox.addView(q);
                for (final Quote it : quotes) {
                    TextView link = UI.text(this, "· " + it.title, 11.5f, Colors.accent(this));
                    link.setPadding(UI.dp(this, 8), UI.dp(this, 3), 0, 0);
                    link.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Intent i = new Intent(ChatActivity.this, PreviewActivity.class);
                            i.putExtra("content", Store.cachedFile(ChatActivity.this, it.path));
                            i.putExtra("title", it.title);
                            startActivity(i);
                        }
                    });
                    quoteBox.addView(link);
                }
                row.addView(quoteBox, UI.lp(WRAP, WRAP));
            }

            LinearLayout actions = UI.row(this, true);
            actions.setPadding(UI.dp(this, 4), UI.dp(this, 8), 0, 0);
            TextView save = UI.text(this, "存为想法", 12f, Colors.accent(this), Typeface.BOLD);
            save.setPadding(0, UI.dp(this, 6), UI.dp(this, 14), UI.dp(this, 6));
            save.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    saveAsNote(text);
                }
            });
            actions.addView(save);

            TextView copy = UI.text(this, "复制", 12f, Colors.text3(dark));
            copy.setPadding(0, UI.dp(this, 6), UI.dp(this, 10), UI.dp(this, 6));
            copy.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    android.content.ClipboardManager cm =
                            (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("ai", text));
                    Banner.show(ChatActivity.this, dark, "已复制", false);
                }
            });
            actions.addView(copy);

            row.addView(actions);
        }

        LinearLayout.LayoutParams rlp = UI.lp(MATCH, WRAP);
        rlp.topMargin = UI.dp(this, 10);
        row.setLayoutParams(rlp);
        messages.addView(row);
        scroll.post(new Runnable() {
            @Override
            public void run() {
                scroll.fullScroll(View.FOCUS_DOWN);
            }
        });
        updateSummarizeVisibility();
    }

    private void updateSummarizeVisibility() {
        if (summarizeBtn == null) return;
        int turns = 0;
        for (Msg m : history) if ("user".equals(m.role)) turns++;
        summarizeBtn.setVisibility(turns >= 3 ? View.VISIBLE : View.GONE);
    }

    // ── 思考占位 ──────────────────────────────────────────
    private LinearLayout thinkingRow;
    private TextView thinkingDots;
    private int dotPhase;
    private final Runnable dotTick = new Runnable() {
        @Override
        public void run() {
            if (thinkingDots == null) return;
            String[] phases = {"·", "··", "···", "··"};
            dotPhase = (dotPhase + 1) % phases.length;
            thinkingDots.setText(phases[dotPhase]);
            thinkingDots.postDelayed(this, 320);
        }
    };

    private void showThinking(AiClient.Model model) {
        hideThinking();
        thinkingRow = UI.row(this, false);
        thinkingRow.setGravity(Gravity.START);

        LinearLayout bubble = UI.row(this, true);
        bubble.setGravity(Gravity.CENTER_VERTICAL);
        bubble.setBackground(UI.roundRect(this, Colors.surface(dark), 14, Colors.line(dark), 0.8f));
        bubble.setPadding(UI.dp(this, 15), UI.dp(this, 11), UI.dp(this, 15), UI.dp(this, 11));

        thinkingDots = UI.text(this, "·", 17f, Colors.text3(dark), Typeface.BOLD);
        bubble.addView(thinkingDots);

        String who = model.isZhipu() ? "GLM" : "DeepSeek";
        TextView label = UI.text(this, "  " + who + " 思考中", 12f, Colors.text3(dark));
        bubble.addView(label);

        thinkingRow.addView(bubble, UI.lp(WRAP, WRAP));
        LinearLayout.LayoutParams rlp = UI.lp(MATCH, WRAP);
        rlp.topMargin = UI.dp(this, 10);
        thinkingRow.setLayoutParams(rlp);
        messages.addView(thinkingRow);
        scrollToBottom();

        dotPhase = 0;
        thinkingDots.postDelayed(dotTick, 250);
    }

    private void hideThinking() {
        if (thinkingDots != null) {
            thinkingDots.removeCallbacks(dotTick);
            thinkingDots = null;
        }
        if (thinkingRow != null) {
            messages.removeView(thinkingRow);
            thinkingRow = null;
        }
    }

    // ── 发送 ──────────────────────────────────────────────
    private void send() {
        if (sending) return;
        String text = input.getText().toString().trim();
        if (text.isEmpty()) return;

        if (text.startsWith("/")) {
            handleCommand(text);
            return;
        }

        final AiClient.Model model = AiClient.currentModel(this);
        final int effort = AiClient.currentEffort(this);
        input.setText("");

        List<Quote> quotes = searchLocal(text);
        addBubble(text, true, null, null, 0);
        history.add(new Msg("user", text, null));

        sending = true;
        showThinking(model);

        AiClient.chat(this, model, effort, buildMessages(quotes), new AiClient.Cb() {
            @Override
            public void ok(String out, String reasoning, long ms) {
                sending = false;
                hideThinking();
                history.add(new Msg("assistant", out, null));
                addBubble(out, false, lastQuotes, reasoning, ms);
                trim();
            }

            @Override
            public void err(String message) {
                sending = false;
                hideThinking();
                addBubble("出错了：" + message, false, null, null, 0);
            }
        });
    }

    private List<Quote> lastQuotes = new ArrayList<Quote>();

    /** 本地检索：标题命中权重高，正文用已缓存的内容 */
    private List<Quote> searchLocal(String query) {
        List<Quote> out = new ArrayList<Quote>();
        String q = query.toLowerCase();
        if (q.length() < 2) {
            lastQuotes = out;
            return out;
        }
        for (GitHubApi.Entry e : known) {
            if (out.size() >= 3) break;
            String name = baseName(e.path);
            if (name.toLowerCase().endsWith(".md")) name = name.substring(0, name.length() - 3);
            String body = Store.cachedFile(this, e.path);
            boolean titleHit = name.toLowerCase().contains(q);
            boolean bodyHit = !body.isEmpty() && body.toLowerCase().contains(q);
            if (!titleHit && !bodyHit) continue;

            String snippet = "";
            if (bodyHit) {
                int at = body.toLowerCase().indexOf(q);
                int from = Math.max(0, at - 60);
                int to = Math.min(body.length(), at + 160);
                snippet = body.substring(from, to).replaceAll("\\s+", " ").trim();
            }
            out.add(new Quote(e.path, name, snippet));
        }
        lastQuotes = out;
        return out;
    }

    private void handleCommand(String raw) {
        input.setText("");
        if (raw.startsWith("/搜")) {
            String q = raw.substring(2).trim();
            if (q.isEmpty()) {
                Banner.show(this, dark, "用法：/搜 关键词", false);
                return;
            }
            List<Quote> hits = searchLocal(q);
            if (hits.isEmpty()) {
                addBubble("本地没找到和「" + q + "」相关的想法", false, null, null, 0);
                return;
            }
            StringBuilder sb = new StringBuilder("找到 " + hits.size() + " 篇：\n");
            for (Quote x : hits) {
                sb.append("\n· ").append(x.title).append("\n  ").append(x.snippet);
            }
            addBubble(sb.toString(), false, hits, null, 0);
            return;
        }
        if (raw.startsWith("/存")) {
            Msg lastAi = null;
            for (int i = history.size() - 1; i >= 0; i--) {
                if ("assistant".equals(history.get(i).role)) {
                    lastAi = history.get(i);
                    break;
                }
            }
            if (lastAi == null) {
                Banner.show(this, dark, "还没有可保存的回答", false);
                return;
            }
            saveAsNote(lastAi.text);
            return;
        }
        Banner.show(this, dark, "可用命令：/搜 关键词、/存", false);
    }

    private JSONArray buildMessages(List<Quote> quotes) {
        JSONArray arr = new JSONArray();
        try {
            StringBuilder sys = new StringBuilder(
                    "你是用户的思路伙伴。回答简洁、具体，优先给可执行的做法，不要用 # 标题符号堆格式。\n"
                            + "主动追问的规则：\n"
                            + "1. 需求缺关键信息时，先用一两个问题问清楚，别猜着往下写。\n"
                            + "2. 给完建议后，如果确实有明显的下一步，可以追问一句（要不要继续细化 / 换个角度 / 直接落成文档）。\n"
                            + "3. 不要每轮都追问，问一次够用就别重复；用户已经在推进时，直接做事而不是再问。\n"
                            + "4. 追问要短，一句话说完，不要罗列一堆可选项堵在用户面前。\n"
                            + "启发的规则：\n"
                            + "5. 用户卡住时，给一个具体的类比或别的领域的做法，不要只给抽象原则。\n"
                            + "6. 发现想法里有明显盲区，直接指出并给一个反例，别用「建议再考虑周全」这种空话。\n"
                            + "7. 最多提一个用户没想到的替代方案；提了就说明它好在哪、代价是什么。\n"
                            + "8. 不要为了显得有深度而堆术语或分点罗列，宁可少说一句。");
            if (contextText != null && !contextText.isEmpty()) {
                String t = contextText.length() > 6000 ? contextText.substring(0, 6000) : contextText;
                sys.append("\n\n【用户当前正在看的想法】\n").append(t);
            }
            if (quotes != null && !quotes.isEmpty()) {
                sys.append("\n\n【从用户仓库里检索到的相关片段】\n");
                for (Quote q : quotes) {
                    sys.append("\n— ").append(q.title).append(" —\n").append(q.snippet).append("\n");
                }
                sys.append("\n如果这些片段有用，可以引用它们的标题。");
            }

            JSONObject s = new JSONObject();
            s.put("role", "system");
            s.put("content", sys.toString());
            arr.put(s);

            for (Msg m : history) {
                JSONObject o = new JSONObject();
                o.put("role", m.role);
                o.put("content", m.text);
                arr.put(o);
            }
        } catch (Exception ignored) {
            // 组装失败就用已加的部分
        }
        return arr;
    }

    private void trim() {
        while (history.size() > MAX_TURNS * 2) history.remove(0);
    }

    // ── 存为想法 ──────────────────────────────────────────
    private String draftTitle;
    private String draftDir;

    /** 从回答里提炼一个短标题：去掉客套、去掉标记，截到第一个句末 */
    private String smartTitle(String text) {
        String s = text == null ? "" : text.trim();
        s = s.replaceAll("^(你好[！!，,。.~～]?|您好[！!，,。.~～]?|当然[，,]?|好的[，,]?|没问题[，,]?)\\s*", "");
        s = s.replaceAll("[#*`>_\\[\\]]", "");
        int cut = s.length();
        for (int i = 0; i < s.length() && i < 40; i++) {
            char c = s.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == '!' || c == '?' || c == '\n') {
                cut = i;
                break;
            }
        }
        s = s.substring(0, Math.min(cut, s.length())).trim();
        if (s.length() > 18) s = s.substring(0, 18);
        return s.isEmpty() ? "AI 想法 " + System.currentTimeMillis() % 100000 : s;
    }

    private void saveAsNote(final String text) {
        final String human = Store.humanRoot(this) + "/";
        final String agent = Store.agentRoot(this) + "/";

        Sheet.Form f = new Sheet.Form();
        f.title = "存为想法";
        f.label = "标题（会作为文件名）";
        f.hint = "给这篇起个名字";
        f.initial = smartTitle(text);
        f.extraLabel = "保存到";
        f.extraValue = human;
        f.extraOptions = new String[]{human, agent};
        f.preview = text;
        f.onSubmit = new Sheet.OnForm() {
            @Override
            public void onSubmit(String value, String extra) {
                String dir = extra == null ? Store.humanRoot(ChatActivity.this) : extra;
                if (dir.endsWith("/")) dir = dir.substring(0, dir.length() - 1);
                String title = value.isEmpty() ? smartTitle(text) : value;
                writeNote(title, text, dir);
            }
        };
        Sheet.showForm(this, dark, f);
    }

    private void writeNote(String title, String text, String dir) {
        String name = title.replaceAll("[\\\\/:*?\"<>|]", "-").trim();
        if (name.isEmpty()) name = "AI 想法 " + System.currentTimeMillis() % 100000;
        String path = dir + "/" + name + ".md";
        String body = "# " + name + "\n\n> V0.1 · " + stamp() + " · raw\n\n" + text.trim() + "\n";
        String token = Store.token(this);
        if (token.isEmpty()) {
            Banner.show(this, dark, "还没填 GitHub token", false);
            return;
        }
        Banner.show(this, dark, "正在保存…", true);
        GitHubApi.writeFile(Store.repo(this), Store.branch(this), path, body, null,
                "docs: 从对话保存 " + name, token, new GitHubApi.Cb<GitHubApi.PutResult>() {
                    @Override
                    public void ok(GitHubApi.PutResult r) {
                        Banner.hide(ChatActivity.this);
                        Banner.show(ChatActivity.this, dark, "已保存到 " + path, false);
                        Store.cacheFile(ChatActivity.this, path, body);
                    }

                    @Override
                    public void err(String message) {
                        Banner.hide(ChatActivity.this);
                        Banner.show(ChatActivity.this, dark, message, false);
                    }
                });
    }

    // ── 整理成一篇 ────────────────────────────────────────
    private void summarizeToNote() {
        if (sending) return;
        final AiClient.Model model = AiClient.currentModel(this);
        final int effort = AiClient.currentEffort(this);
        sending = true;
        Banner.show(this, dark, "正在整理这段讨论…", true);

        JSONArray arr = new JSONArray();
        try {
            JSONObject s = new JSONObject();
            s.put("role", "system");
            s.put("content", "把下面这段对话整理成一篇结构清晰的中文 Markdown 笔记。"
                    + "要求：有 H1 标题、分小节、保留关键结论和代码，删掉寒暄和重复。"
                    + "只输出 Markdown 正文，不要解释。");
            arr.put(s);
            StringBuilder sb = new StringBuilder();
            for (Msg m : history) {
                sb.append("user".equals(m.role) ? "用户：" : "助手：");
                sb.append(m.text).append("\n\n");
            }
            JSONObject u = new JSONObject();
            u.put("role", "user");
            u.put("content", sb.toString());
            arr.put(u);
        } catch (Exception ignored) {
            // 忽略
        }

        AiClient.chat(this, model, effort, arr, new AiClient.Cb() {
            @Override
            public void ok(String out, String reasoning, long ms) {
                sending = false;
                Banner.hide(ChatActivity.this);
                Intent i = new Intent(ChatActivity.this, EditorActivity.class);
                i.putExtra("prefill", out);
                startActivity(i);
                overridePendingTransition(R.anim.enter, R.anim.exit);
            }

            @Override
            public void err(String message) {
                sending = false;
                Banner.hide(ChatActivity.this);
                Banner.show(ChatActivity.this, dark, message, false);
            }
        });
    }

    private String stamp() {
        java.text.SimpleDateFormat f =
                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US);
        return f.format(new java.util.Date());
    }

    private TextView chip(String text, int color, int bg) {
        TextView t = UI.text(this, text, 11.5f, color, Typeface.BOLD);
        t.setBackground(UI.roundRect(this, bg, 8));
        t.setPadding(UI.dp(this, 9), UI.dp(this, 5), UI.dp(this, 9), UI.dp(this, 5));
        t.setMaxLines(1);
        return t;
    }

    /** 切换后弹一下，让「刚才点了它」有反馈 */
    private void bump(final View v) {
        if (v == null) return;
        v.animate().cancel();
        v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(90)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(200).start();
                    }
                }).start();
    }

    private void refreshModelLabel() {
        if (modelLabel == null || effortLabel == null) return;
        AiClient.Model m = AiClient.currentModel(this);
        modelLabel.setText(m.label + "  ▾");
        effortLabel.setText(AiClient.effortLabel(m, AiClient.currentEffort(this)) + "  ▾");

        modelLabel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickModel();
            }
        });
        effortLabel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickEffort();
            }
        });
    }

    private void pickModel() {
        String[] labels = new String[AiClient.MODELS.length];
        for (int i = 0; i < labels.length; i++) labels[i] = AiClient.MODELS[i].label;
        Sheet.showOptions(this, dark, "模型", null, labels, new Sheet.OnPick() {
            @Override
            public void onPick(int index) {
                Store.setInt(ChatActivity.this, "aiModelIndex", index);
                AiClient.Model m = AiClient.MODELS[index];
                if (AiClient.keyFor(ChatActivity.this, m.provider).isEmpty()) {
                    Banner.show(ChatActivity.this, dark,
                            "选中了「" + m.label + "」，但还没填它的 Key（去设置）", false);
                }
                refreshModelLabel();
                bump(modelLabel);
                showWelcome();
            }
        });
    }

    private void pickEffort() {
        AiClient.Model m = AiClient.currentModel(this);
        if (m.isZhipu()) {
            Sheet.showOptions(this, dark, "思考", "智谱只有开 / 关", new String[]{"不思考", "思考开"},
                    new Sheet.OnPick() {
                        @Override
                        public void onPick(int index) {
                            Store.setInt(ChatActivity.this, "aiEffort",
                                    index == 0 ? AiClient.EFFORT_OFF : AiClient.EFFORT_HIGH);
                            refreshModelLabel();
                            bump(effortLabel);
                        }
                    });
            return;
        }
        Sheet.showOptions(this, dark, "思考强度", "DeepSeek 支持分级", AiClient.EFFORT_LABELS,
                new Sheet.OnPick() {
                    @Override
                    public void onPick(int index) {
                        Store.setInt(ChatActivity.this, "aiEffort", index);
                        refreshModelLabel();
                        bump(effortLabel);
                    }
                });
    }

    private View iconButton(int res, View.OnClickListener l) {
        FrameLayout box = new FrameLayout(this);
        box.setBackground(UI.roundRect(this, 0x00000000, 10));
        ImageView ic = UI.icon(this, res, 20, Colors.text2(dark));
        box.addView(ic, new FrameLayout.LayoutParams(UI.dp(this, 20), UI.dp(this, 20), Gravity.CENTER));
        box.setLayoutParams(UI.lp(UI.dp(this, 38), UI.dp(this, 38)));
        box.setOnClickListener(l);
        UI.press(box);
        return box;
    }
}
