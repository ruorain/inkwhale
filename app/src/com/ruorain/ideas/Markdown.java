package com.ruorain.ideas;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 极简 Markdown → HTML，给 WebView 预览用。不引三方库。 */
public final class Markdown {

    private static final Pattern TABLE_SEP = Pattern.compile("^\\|[\\s:|-]+\\|?\\s*$");

    private Markdown() {}

    public static String toHtml(String md, boolean dark, int accentColor) {
        StringBuilder body = new StringBuilder();
        String[] lines = (md == null ? "" : md).replace("\r\n", "\n").replace("\r", "\n").split("\n", -1);

        boolean inCode = false;
        boolean listOpen = false;
        boolean olOpen = false;
        boolean quoteOpen = false;
        boolean taskOpen = false;
        String codeBuf = null;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String t = line.trim();

            // 代码块
            if (t.startsWith("```")) {
                if (!inCode) {
                    closeLists(body, listOpen, olOpen);
                    listOpen = false;
                    olOpen = false;
                    closeQuote(body, quoteOpen);
                    quoteOpen = false;
                    body.append("<pre><code>");
                    inCode = true;
                } else {
                    body.append("</code></pre>");
                    inCode = false;
                }
                continue;
            }
            if (inCode) {
                body.append(escape(line)).append('\n');
                continue;
            }

            // 任务列表遇到非任务行就收口，否则 </ul> 会跑到 <hr>/<table> 后面
            boolean isTaskLine = t.matches("^[-*+]\\s+\\[[ xX]\\]\\s+.*$");
            if (taskOpen && !isTaskLine) {
                body.append("</ul>");
                taskOpen = false;
            }

            // 表格
            if (t.startsWith("|") && i + 1 < lines.length && TABLE_SEP.matcher(lines[i + 1].trim()).matches()) {
                closeLists(body, listOpen, olOpen);
                listOpen = false;
                olOpen = false;
                closeQuote(body, quoteOpen);
                quoteOpen = false;

                body.append("<table><thead><tr>");
                for (String c : splitRow(t)) body.append("<th>").append(inline(c)).append("</th>");
                body.append("</tr></thead><tbody>");
                i += 2;
                while (i < lines.length && lines[i].trim().startsWith("|")) {
                    body.append("<tr>");
                    for (String c : splitRow(lines[i].trim())) body.append("<td>").append(inline(c)).append("</td>");
                    body.append("</tr>");
                    i++;
                }
                body.append("</tbody></table>");
                i--;
                continue;
            }

            // 空行
            if (t.isEmpty()) {
                closeLists(body, listOpen, olOpen);
                listOpen = false;
                olOpen = false;
                closeQuote(body, quoteOpen);
                quoteOpen = false;
                continue;
            }

            // 分隔线
            if (t.matches("^(-{3,}|\\*{3,}|_{3,})$")) {
                closeLists(body, listOpen, olOpen);
                listOpen = false;
                olOpen = false;
                closeQuote(body, quoteOpen);
                quoteOpen = false;
                body.append("<hr>");
                continue;
            }

            // 标题
            Matcher h = Pattern.compile("^(#{1,6})\\s+(.*)$").matcher(t);
            if (h.matches()) {
                closeLists(body, listOpen, olOpen);
                listOpen = false;
                olOpen = false;
                closeQuote(body, quoteOpen);
                quoteOpen = false;
                int level = h.group(1).length();
                body.append("<h").append(level).append(">").append(inline(h.group(2))).append("</h").append(level).append(">");
                continue;
            }

            // 引用
            if (t.startsWith(">")) {
                closeLists(body, listOpen, olOpen);
                listOpen = false;
                olOpen = false;
                if (!quoteOpen) {
                    body.append("<blockquote>");
                    quoteOpen = true;
                }
                body.append("<p>").append(inline(t.substring(1).trim())).append("</p>");
                continue;
            }
            closeQuote(body, quoteOpen);
            quoteOpen = false;

            // 任务列表
            Matcher task = Pattern.compile("^[-*+]\\s+\\[([ xX])\\]\\s+(.*)$").matcher(t);
            if (task.matches()) {
                closeLists(body, listOpen, olOpen);
                listOpen = false;
                olOpen = false;
                if (!taskOpen) {
                    body.append("<ul class=\"tasks\">");
                    taskOpen = true;
                }
                boolean done = !" ".equals(task.group(1));
                body.append("<li><span class=\"cb").append(done ? " on" : "").append("\">")
                        .append(done ? "✓" : "").append("</span><span>")
                        .append(inline(task.group(2))).append("</span></li>");
                continue;
            }
            if (taskOpen) {
                body.append("</ul>");
                taskOpen = false;
            }

            // 无序列表
            Matcher ul = Pattern.compile("^[-*+]\\s+(.*)$").matcher(t);
            if (ul.matches()) {
                if (!listOpen) {
                    closeLists(body, false, olOpen);
                    olOpen = false;
                    body.append("<ul>");
                    listOpen = true;
                }
                body.append("<li>").append(inline(ul.group(1))).append("</li>");
                continue;
            }

            // 有序列表
            Matcher ol = Pattern.compile("^\\d+[.)]\\s+(.*)$").matcher(t);
            if (ol.matches()) {
                if (!olOpen) {
                    closeLists(body, listOpen, false);
                    listOpen = false;
                    body.append("<ol>");
                    olOpen = true;
                }
                body.append("<li>").append(inline(ol.group(1))).append("</li>");
                continue;
            }

            closeLists(body, listOpen, olOpen);
            listOpen = false;
            olOpen = false;

            // 段落
            body.append("<p>").append(inline(t)).append("</p>");
        }

        closeCode(body, inCode);
        closeLists(body, listOpen, olOpen);
        closeQuote(body, quoteOpen);
        if (taskOpen) body.append("</ul>");

        return wrap(body.toString(), dark, accentColor);
    }

    private static void closeCode(StringBuilder b, boolean inCode) {
        if (inCode) b.append("</code></pre>");
    }

    private static void closeLists(StringBuilder b, boolean ul, boolean ol) {
        if (ul) b.append("</ul>");
        if (ol) b.append("</ol>");
    }

    private static void closeQuote(StringBuilder b, boolean q) {
        if (q) b.append("</blockquote>");
    }

    private static String[] splitRow(String row) {
        String s = row;
        if (s.startsWith("|")) s = s.substring(1);
        if (s.endsWith("|")) s = s.substring(0, s.length() - 1);
        String[] parts = s.split("\\|", -1);
        for (int i = 0; i < parts.length; i++) parts[i] = parts[i].trim();
        return parts;
    }

    private static String inline(String s) {
        String x = escape(s);
        x = x.replaceAll("`([^`]+)`", "<code>$1</code>");
        x = x.replaceAll("\\*\\*([^*]+)\\*\\*", "<strong>$1</strong>");
        x = x.replaceAll("(?<!\\*)\\*([^*]+)\\*(?!\\*)", "<em>$1</em>");
        x = x.replaceAll("~~([^~]+)~~", "<del>$1</del>");
        x = x.replaceAll("!\\[([^\\]]*)\\]\\(([^)\\s]+)\\)", "<img alt=\"$1\" src=\"$2\">");
        x = x.replaceAll("\\[([^\\]]+)\\]\\(([^)]+)\\)", "<a href=\"$2\">$1</a>");
        return x;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String wrap(String body, boolean dark, int accentColor) {
        String bg = dark ? "#0D0D0E" : "#FBFBFB";
        String fg = dark ? "#EDEDEE" : "#161616";
        String fg2 = dark ? "#9C9CA2" : "#6B6B70";
        String fg3 = dark ? "#6C6C73" : "#9A9AA0";
        String surface = dark ? "#1F1F21" : "#F1F1F1";
        String line = dark ? "#26262A" : "#E8E8E8";
        String accent = String.format("#%06X", 0xFFFFFF & accentColor);

        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<style>"
                + "html,body{margin:0;padding:0;background:" + bg + ";color:" + fg + ";}"
                + "body{font:15.2px/1.75 -apple-system,'PingFang SC','Microsoft YaHei',sans-serif;"
                + "padding:16px 20px 60px;-webkit-text-size-adjust:100%;}"
                + "h1{font-size:24px;line-height:1.32;font-weight:700;letter-spacing:-.02em;margin:6px 0 14px;}"
                + "h2{font-size:17.5px;line-height:1.4;font-weight:650;margin:26px 0 9px;}"
                + "h3{font-size:15px;font-weight:650;margin:20px 0 6px;}"
                + "h4,h5,h6{font-size:14.5px;font-weight:600;margin:16px 0 6px;color:" + fg2 + ";}"
                + "p{margin:0 0 13px;}"
                + "ul,ol{margin:0 0 13px;padding-left:20px;}"
                + "li{margin:5px 0;}"
                + "blockquote{margin:0 0 14px;padding:2px 0 2px 13px;border-left:2.5px solid " + accent + ";color:" + fg2 + ";}"
                + "blockquote p{margin:0;}"
                + "code{font-family:ui-monospace,Menlo,Consolas,monospace;font-size:12.8px;"
                + "background:" + surface + ";padding:1.5px 4.5px;border-radius:4px;}"
                + "pre{background:" + surface + ";border-radius:10px;padding:13px 14px;overflow-x:auto;margin:0 0 15px;"
                + "border:1px solid " + line + ";}"
                + "pre code{background:none;padding:0;font-size:12.4px;line-height:1.7;white-space:pre-wrap;word-break:break-word;}"
                + "table{width:100%;border-collapse:collapse;margin:0 0 15px;font-size:13.5px;}"
                + "th,td{border:1px solid " + line + ";padding:7px 10px;text-align:left;}"
                + "th{background:" + surface + ";font-weight:600;font-size:12.5px;color:" + fg2 + ";}"
                + "hr{border:0;border-top:1px solid " + line + ";margin:22px 0;}"
                + "img{max-width:100%;border-radius:8px;display:block;border:1px solid " + line + ";}"
                + "a{color:" + accent + ";text-decoration:none;}"
                + "ul.tasks{list-style:none;padding-left:2px;}"
                + "ul.tasks li{display:flex;gap:9px;align-items:flex-start;margin:7px 0;}"
                + ".cb{width:15px;height:15px;border:1.5px solid " + line + ";border-radius:4px;flex:none;"
                + "margin-top:3px;display:flex;align-items:center;justify-content:center;"
                + "font-size:11px;line-height:1;}"
                + ".cb.on{background:" + accent + ";border-color:" + accent + ";color:#fff;}"
                + "strong{font-weight:650;}"
                + "</style></head><body>" + body + "</body></html>";
    }
}
