package com.trip.adaptive.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.trip.adaptive.domain.TravelGuide;
import com.trip.adaptive.repository.TravelGuideRepository;

/**
 * 首页智能助手：回答用户的旅行问题。
 *
 * <p>回答分三层：先在攻略社区里检索同伴写过的真实攻略，再匹配站内目录导航（“在哪里看事件监测”这类问题），
 * 最后把这些站内素材作为上下文交给大模型自由作答；模型不可用时退回到纯站内素材的规则回答。
 */
@Service
public class AssistantService {
  private static final int MAX_GUIDES = 3;
  private static final int MAX_LINKS = 4;

  /** 站内目录：用于“XX 功能在哪里”这类导航问题，同时作为大模型回答的事实依据。 */
  private static final List<NavEntry> CATALOG =
      List.of(
          new NavEntry("首页", "/", "行程概览、风险仪表盘与团队动态", List.of("首页", "主页", "概览", "仪表")),
          new NavEntry(
              "行程总览", "/trips", "查看与管理全部行程，群主可在这里删除行程", List.of("行程", "路线安排", "删除行程", "新建行程")),
          new NavEntry("地图路线", "/routes", "在地图上查看节点顺序与通勤方式", List.of("地图", "路线", "导航", "交通")),
          new NavEntry(
              "我的小组",
              "/groups",
              "创建/加入小组，群主可转移群主或解散小组，成员可退出小组",
              List.of("小组", "群组", "解散", "退出", "成员", "房间码")),
          new NavEntry("好友与邀请", "/friends", "搜索好友、处理好友申请", List.of("好友", "邀请", "加人")),
          new NavEntry("聊天", "/chat", "与好友或小组实时沟通，可分享攻略", List.of("聊天", "私聊", "群聊", "消息")),
          new NavEntry("攻略社区", "/guides", "浏览、收藏并发布旅行攻略", List.of("攻略", "社区", "游记", "推荐", "收藏")),
          new NavEntry(
              "事件监测",
              "/events",
              "查看天气与城市事件，可手动拉取最新事件",
              List.of("事件", "监测", "天气", "预警", "台风", "下雨")),
          new NavEntry("影响与风险", "/impacts", "查看事件命中了哪些节点以及风险评分", List.of("影响", "风险", "评分", "受影响")),
          new NavEntry("替代方案", "/plans", "对比三种重规划策略并发起投票", List.of("替代", "方案", "重规划", "改行程")),
          new NavEntry("投票中心", "/votes", "参与方案投票并唱票", List.of("投票", "表决", "唱票")),
          new NavEntry(
              "变更记录", "/trips", "在行程详情页打开“变更记录”，查看已应用/已回退的变更明细", List.of("变更", "记录", "回退", "历史")),
          new NavEntry("预算与费用", "/budget", "登记花费并跟踪预算", List.of("预算", "花费", "费用", "记账")),
          new NavEntry("分账与结算", "/settlement", "查看成员之间应收应付", List.of("分账", "结算", "AA", "还钱")),
          new NavEntry("讨论区", "/discussions", "围绕行程展开讨论", List.of("讨论", "帖子", "留言")),
          new NavEntry("通知", "/notifications", "接收方案、投票与事件提醒", List.of("通知", "提醒", "消息中心")),
          new NavEntry("个人设置", "/settings", "修改资料、密码与账号", List.of("设置", "资料", "密码", "账号")));

  private final TravelGuideRepository guides;
  private final AiClient ai;

  public AssistantService(TravelGuideRepository guides, AiClient ai) {
    this.guides = guides;
    this.ai = ai;
  }

  public AssistantAnswer ask(String question) {
    String q = question == null ? "" : question.trim();
    if (q.isEmpty()) {
      return new AssistantAnswer(
          "说说你想去哪、和谁去、几天，我来帮你挑地方或带你找到对应的功能页面。", "offline", List.of(), List.of());
    }
    List<GuideHint> matchedGuides = searchGuides(q);
    List<NavEntry> matchedLinks = searchCatalog(q);
    String aiAnswer = ai.chatText(systemPrompt(), userPrompt(q, matchedGuides, matchedLinks));
    if (aiAnswer != null) {
      return new AssistantAnswer(aiAnswer, "ai", matchedGuides, toHints(matchedLinks));
    }
    return new AssistantAnswer(
        offlineAnswer(matchedGuides, matchedLinks),
        matchedGuides.isEmpty() && matchedLinks.isEmpty() ? "offline" : "local",
        matchedGuides,
        toHints(matchedLinks));
  }

  /** 攻略社区检索：按标题/城市/主题/标签/描述的关键词命中数排序。 */
  private List<GuideHint> searchGuides(String question) {
    Set<String> tokens = tokenize(question);
    if (tokens.isEmpty()) return List.of();
    record Scored(TravelGuide guide, int score) {}
    return guides.findAllByOrderByCreatedAtDesc().stream()
        .map(guide -> new Scored(guide, score(guide, tokens)))
        .filter(scored -> scored.score() > 0)
        .sorted(
            Comparator.comparingInt(Scored::score)
                .reversed()
                .thenComparing(scored -> -scored.guide().getRating()))
        .limit(MAX_GUIDES)
        .map(
            scored ->
                new GuideHint(
                    scored.guide().getId(),
                    scored.guide().getTitle(),
                    scored.guide().getCity(),
                    scored.guide().getTheme(),
                    scored.guide().getDays(),
                    scored.guide().getRating(),
                    scored.guide().getDescription()))
        .toList();
  }

  private int score(TravelGuide guide, Set<String> tokens) {
    String haystack =
        String.join(
                " ",
                nz(guide.getTitle()),
                nz(guide.getCity()),
                nz(guide.getTheme()),
                nz(guide.getDescription()),
                guide.getTags() == null ? "" : String.join(" ", guide.getTags()))
            .toLowerCase();
    int score = 0;
    for (String token : tokens) {
      if (haystack.contains(token)) score++;
    }
    return score;
  }

  private List<NavEntry> searchCatalog(String question) {
    String lower = question.toLowerCase();
    return CATALOG.stream()
        .filter(
            entry ->
                entry.keywords().stream().anyMatch(keyword -> lower.contains(keyword.toLowerCase()))
                    || lower.contains(entry.label()))
        .limit(MAX_LINKS)
        .toList();
  }

  /**
   * 中英混排的轻量分词：中文按 2 字滑窗切片（“重庆火锅”→ 重庆/庆火/火锅），英文/数字按空白与标点切分。
   *
   * <p>攻略数量不大，直接做包含匹配即可，不引入额外分词依赖。
   */
  private Set<String> tokenize(String question) {
    Set<String> tokens = new LinkedHashSet<>();
    StringBuilder latin = new StringBuilder();
    String cleaned = question.toLowerCase();
    for (int i = 0; i < cleaned.length(); i++) {
      char c = cleaned.charAt(i);
      if (Character.isLetterOrDigit(c) && c < 128) {
        latin.append(c);
        continue;
      }
      if (latin.length() >= 2) tokens.add(latin.toString());
      latin.setLength(0);
      if (isCjk(c) && i + 1 < cleaned.length() && isCjk(cleaned.charAt(i + 1))) {
        tokens.add(cleaned.substring(i, i + 2));
      }
    }
    if (latin.length() >= 2) tokens.add(latin.toString());
    return tokens;
  }

  private static boolean isCjk(char c) {
    return c >= 0x4E00 && c <= 0x9FFF;
  }

  private String systemPrompt() {
    String catalog =
        CATALOG.stream()
            .map(
                entry ->
                    String.format("%s（%s）：%s", entry.label(), entry.path(), entry.description()))
            .collect(Collectors.joining("；"));
    return "你是“智行”旅行协作平台的站内助手。用简体中文回答，控制在 150 字以内，语气自然、给具体建议。"
        + "回答旅行推荐时优先引用给出的社区攻略，并说明推荐理由；没有可引用的攻略时再凭常识回答，"
        + "但不要编造站内不存在的攻略标题。回答功能导航问题时，请指出对应页面名称。站内目录如下："
        + catalog;
  }

  private String userPrompt(String question, List<GuideHint> matched, List<NavEntry> links) {
    StringBuilder prompt = new StringBuilder("用户提问：").append(question).append('\n');
    if (matched.isEmpty()) {
      prompt.append("社区攻略检索结果：无匹配。\n");
    } else {
      prompt.append("社区攻略检索结果：\n");
      for (GuideHint hint : matched) {
        prompt.append(
            String.format(
                "- %s（%s · %s · %d 天 · 评分 %.1f）：%s%n",
                hint.title(),
                nz(hint.city()),
                nz(hint.theme()),
                hint.days(),
                hint.rating(),
                nz(hint.description())));
      }
    }
    if (!links.isEmpty()) {
      prompt.append("可能相关的站内页面：");
      prompt.append(
          links.stream()
              .map(entry -> entry.label() + "(" + entry.path() + ")")
              .collect(Collectors.joining("、")));
    }
    return prompt.toString();
  }

  /** 直接用检索到的站内素材组织回答。 */
  private String offlineAnswer(List<GuideHint> matched, List<NavEntry> links) {
    List<String> parts = new ArrayList<>();
    if (!matched.isEmpty()) {
      parts.add(
          "攻略社区里有这些相关内容："
              + matched.stream()
                  .map(hint -> hint.title() + "（" + nz(hint.city()) + "）")
                  .collect(Collectors.joining("、")));
    }
    if (!links.isEmpty()) {
      parts.add(
          "你要找的功能可能在："
              + links.stream()
                  .map(entry -> entry.label() + " " + entry.path())
                  .collect(Collectors.joining("、")));
    }
    if (parts.isEmpty()) {
      return "告诉我城市和天数，我就能帮你找到对应的攻略和行程建议；也可以先去“攻略社区”看同伴写过的真实行程。";
    }
    return String.join("；", parts) + "。";
  }

  private List<NavHint> toHints(List<NavEntry> entries) {
    return entries.stream()
        .map(entry -> new NavHint(entry.label(), entry.path(), entry.description()))
        .toList();
  }

  private static String nz(String value) {
    return value == null ? "" : value;
  }

  private record NavEntry(String label, String path, String description, List<String> keywords) {}

  public record NavHint(String label, String path, String description) {}

  public record GuideHint(
      Long id,
      String title,
      String city,
      String theme,
      int days,
      double rating,
      String description) {}

  public record AssistantAnswer(
      String answer, String source, List<GuideHint> guides, List<NavHint> links) {}
}
