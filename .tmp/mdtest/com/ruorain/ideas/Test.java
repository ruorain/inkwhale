package com.ruorain.ideas;
public class Test {
    public static void main(String[] args) {
        String md = "# 离线优先\n\n## 背景\n\n正文里 **粗体**、`行内代码`、~~删除线~~、[链接](https://x.y)。\n\n"
            + "- 第一项\n- 第二项\n\n1. 有序一\n2. 有序二\n\n> 引用一行\n\n"
            + "```kotlin\nfun x() = 1\n```\n\n"
            + "| 场景 | 处理 |\n| --- | --- |\n| sha 一致 | 直接写入 |\n\n"
            + "- [x] 完成项\n- [ ] 待办项\n\n---\n\n结尾。\n";
        String html = Markdown.toHtml(md, false, 0xFF2F6FED);
        System.out.println("HTML 长度: " + html.length());
        System.out.println("h1=" + html.contains("<h1>离线优先</h1>")
            + " strong=" + html.contains("<strong>粗体</strong>")
            + " code=" + html.contains("<code>行内代码</code>")
            + " del=" + html.contains("<del>删除线</del>")
            + " link=" + html.contains("<a href=\"https://x.y\">链接</a>"));
        System.out.println("ul=" + html.contains("<ul>") + " ol=" + html.contains("<ol>")
            + " quote=" + html.contains("<blockquote>") + " pre=" + html.contains("<pre><code>"));
        System.out.println("table=" + html.contains("<table>") + " th=" + html.contains("<th>场景</th>")
            + " tasks=" + html.contains("ul class=\"tasks\"") + " checked=" + html.contains("cb on")
            + " hr=" + html.contains("<hr>"));
        System.out.println("accent=" + html.contains("#2F6FED"));
        int i = html.indexOf("<body>");
        System.out.println("---- body ----");
        System.out.println(html.substring(i, Math.min(i + 700, html.length())));
    }
}
