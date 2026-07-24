package com.trip.adaptive.config;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.trip.adaptive.domain.TravelGuide;
import com.trip.adaptive.domain.User;
import com.trip.adaptive.repository.TravelGuideRepository;
import com.trip.adaptive.repository.UserRepository;

/**
 * 独立于演示种子的「攻略社区内容」初始化：仅补充一批真实可读的攻略内容，供社区首屏不至于空白。 与 {@link DataSeeder}
 * 分开、由独立开关控制，因此清空演示数据后仍可单独启用攻略内容。 关键：不制造任何虚假互动——评分、收藏、评论一律从 0 起步，只有用户真实点赞/收藏/评论才会累积。
 */
@Component
@Order(20)
public class GuideContentSeeder implements CommandLineRunner {
  private static final Logger log = LoggerFactory.getLogger(GuideContentSeeder.class);

  private final UserRepository users;
  private final TravelGuideRepository guides;
  private final PasswordEncoder passwordEncoder;

  @Value("${app.seed.guides.enabled:false}")
  boolean enabled;

  public GuideContentSeeder(
      UserRepository users, TravelGuideRepository guides, PasswordEncoder passwordEncoder) {
    this.users = users;
    this.guides = guides;
    this.passwordEncoder = passwordEncoder;
  }

  public void run(String... args) {
    if (!enabled) return;
    User curator =
        users
            .findByEmail("editor@guides.local")
            .orElseGet(
                () ->
                    users.save(
                        new User(
                            "攻略编辑部",
                            "editor@guides.local",
                            passwordEncoder.encode("password123"),
                            "13800000010")));

    int created = 0;
    for (GuideSeed g : curatedGuides()) {
      if (guides.existsByTitle(g.title())) continue;
      TravelGuide guide = new TravelGuide();
      guide.setAuthor(curator);
      guide.setTitle(g.title());
      guide.setCity(g.city());
      guide.setDays(g.days());
      guide.setTheme(g.theme());
      guide.setPrice(new BigDecimal(g.price()));
      guide.setCover(g.cover());
      guide.setDescription(g.description());
      guide.setTags(new ArrayList<>(g.tags()));
      // 真实互动从 0 起步，不预置任何评分/评论/收藏。
      guide.setRating(0);
      guide.setReviews(0);
      guide.setSaves(0);
      guides.save(guide);
      created++;
    }
    if (created > 0) log.info("seeded {} community guides (engagement starts at zero)", created);
  }

  private List<GuideSeed> curatedGuides() {
    return List.of(
        new GuideSeed(
            "上海春日漫游：从梧桐区走到黄浦江",
            "上海",
            2,
            "城市漫游",
            "980",
            "https://images.unsplash.com/photo-1548919973-5cef591cdbc9?auto=format&fit=crop&w=900&q=80",
            "把外滩、武康路和一顿本帮菜，放进一个松弛的周末。第一天沿武康路、安福路慢走，傍晚到外滩看灯；" + "第二天豫园早茶后去田子坊逛小店。",
            List.of("Citywalk", "咖啡", "拍照")),
        new GuideSeed(
            "成都不赶路：熊猫、茶馆和一场慢火锅",
            "成都",
            3,
            "美食探索",
            "1280",
            "https://images.unsplash.com/photo-1548013146-72479768bada?auto=format&fit=crop&w=900&q=80",
            "不塞满景点，留出午后在人民公园喝茶的时间。上午熊猫基地，午后宽窄巷子，晚上锦里夜色配一顿慢火锅。",
            List.of("熊猫", "火锅", "慢生活")),
        new GuideSeed(
            "杭州西湖：一条不重复的环湖路线",
            "杭州",
            2,
            "自然风光",
            "860",
            "https://images.unsplash.com/photo-1538485399081-7c897a9a3d20?auto=format&fit=crop&w=900&q=80",
            "从北山街的清晨开始，断桥、苏堤、龙井村一路串起，把湖光山色交给脚步，避开人挤人的主入口。",
            List.of("西湖", "骑行", "茶园")),
        new GuideSeed(
            "北京中轴线：把六百年走成一天",
            "北京",
            3,
            "城市漫游",
            "1120",
            "https://images.unsplash.com/photo-1508804185872-d7badad00f7d?auto=format&fit=crop&w=900&q=80",
            "从永定门到钟鼓楼，沿着中轴线读懂这座城。故宫需提前预约，胡同段留给傍晚，最后登钟鼓楼看城市天际线。",
            List.of("故宫", "胡同", "历史")),
        new GuideSeed(
            "厦门海岛慢时光：鼓浪屿到环岛路",
            "厦门",
            2,
            "疗愈放空",
            "940",
            "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=900&q=80",
            "把海风、老别墅和一碗沙茶面留给自己。上午渡轮上鼓浪屿看老建筑，午后骑行环岛路，黄昏在沙滩收尾。",
            List.of("海岛", "骑行", "文艺")),
        new GuideSeed(
            "西安古都寻踪：城墙内外的烟火",
            "西安",
            3,
            "美食探索",
            "1050",
            "https://images.unsplash.com/photo-1591122947157-26bad3a117d2?auto=format&fit=crop&w=900&q=80",
            "兵马俑、回民街和一段夜色里的大雁塔。第一天兵马俑，第二天城墙骑行加回坊小吃，夜里看大雁塔喷泉。",
            List.of("历史", "小吃", "夜景")));
  }

  private record GuideSeed(
      String title,
      String city,
      int days,
      String theme,
      String price,
      String cover,
      String description,
      List<String> tags) {}
}
