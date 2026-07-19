package com.trip.adaptive.config;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.trip.adaptive.domain.Enums;
import com.trip.adaptive.domain.Friendship;
import com.trip.adaptive.domain.GroupMember;
import com.trip.adaptive.domain.ItineraryNode;
import com.trip.adaptive.domain.MemberConstraint;
import com.trip.adaptive.domain.TravelGroup;
import com.trip.adaptive.domain.Trip;
import com.trip.adaptive.domain.User;
import com.trip.adaptive.repository.FriendshipRepository;
import com.trip.adaptive.repository.GroupMemberRepository;
import com.trip.adaptive.repository.ItineraryNodeRepository;
import com.trip.adaptive.repository.MemberConstraintRepository;
import com.trip.adaptive.repository.TravelGroupRepository;
import com.trip.adaptive.repository.TripRepository;
import com.trip.adaptive.repository.UserRepository;

@Component
public class DataSeeder implements CommandLineRunner {
  private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
  private final UserRepository users;
  private final TravelGroupRepository groups;
  private final GroupMemberRepository members;
  private final MemberConstraintRepository constraints;
  private final FriendshipRepository friendships;
  private final TripRepository trips;
  private final ItineraryNodeRepository nodes;
  private final PasswordEncoder passwordEncoder;

  @Value("${app.seed.enabled:true}")
  boolean enabled;

  public DataSeeder(
      UserRepository u,
      TravelGroupRepository g,
      GroupMemberRepository m,
      MemberConstraintRepository c,
      FriendshipRepository f,
      TripRepository t,
      ItineraryNodeRepository n,
      PasswordEncoder p) {
    users = u;
    groups = g;
    members = m;
    constraints = c;
    friendships = f;
    trips = t;
    nodes = n;
    passwordEncoder = p;
  }

  public void run(String... args) {
    if (!enabled) return;

    // Demo accounts are created only when missing (keyed by email), so an
    // already-populated database keeps its rows and simply gains the accounts
    // that are still absent. Existing rows are never overwritten or duplicated.
    User a = ensureUser("张三", "zhangsan@example.com", "13800000001");
    User b = ensureUser("李四", "lisi@example.com", "13800000002");
    User wangwu = ensureUser("王五", "wangwu@example.com", "13800000005");
    ensureUser("赵六", "zhaoliu@example.com", "13800000006");

    if (groups.findByRoomCode("CN-DEMO-01").isEmpty()) {
      TravelGroup nationwide = new TravelGroup("全国漫游示例", "全国城市示例行程", a);
      nationwide.setRoomCode("CN-DEMO-01");
      nationwide = groups.save(nationwide);
      members.save(new GroupMember(nationwide, a, Enums.MemberRole.OWNER));
      seedNationwideTrips(nationwide);
      log.info("seeded nationwide demo bundle: group {} with 34 trips", nationwide.getId());
    }

    // The social bundle (friendships, group, members, trip, nodes) is seeded once,
    // anchored on the demo group. Once it exists the bundle is skipped, so
    // intentional deletes (removing a friend/member, deleting the trip) are not
    // resurrected on the next startup.
    if (groups.findByRoomCode("SH24-7K").isPresent()) return;

    ensureFriendship(a, b, Enums.FriendshipStatus.ACCEPTED);
    ensureFriendship(wangwu, a, Enums.FriendshipStatus.PENDING);

    TravelGroup g = new TravelGroup("上海周末小队", "演示群组", a);
    g.setRoomCode("SH24-7K");
    g = groups.save(g);
    GroupMember ma = members.save(new GroupMember(g, a, Enums.MemberRole.OWNER));
    GroupMember mb = members.save(new GroupMember(g, b, Enums.MemberRole.MEMBER));
    for (GroupMember m : List.of(ma, mb)) {
      MemberConstraint c =
          new MemberConstraint(
              m, LocalDate.now().plusDays(7), LocalDate.now().plusDays(10), new BigDecimal("500"));
      c.setMustVisitPlaces(List.of("外滩", "豫园"));
      c.setFitnessLevel(Enums.FitnessLevel.MEDIUM);
      constraints.save(c);
    }

    LocalDate tripStart = LocalDate.now().plusDays(2);
    Trip trip = new Trip();
    trip.setGroup(g);
    trip.setTitle("上海春日漫游");
    trip.setStatus(Enums.TripStatus.ONGOING);
    trip.setStartDate(tripStart);
    trip.setEndDate(tripStart.plusDays(2));
    trip.setTotalBudget(new BigDecimal("1800"));
    trip = trips.save(trip);
    saveNode(
        trip,
        "外滩晨光",
        "外滩",
        31.2304,
        121.4737,
        Enums.NodeType.ATTRACTION,
        tripStart.atTime(9, 0),
        tripStart.atTime(11, 0),
        1,
        new BigDecimal("0"));
    saveNode(
        trip,
        "豫园午后",
        "豫园",
        31.2271,
        121.492,
        Enums.NodeType.ATTRACTION,
        tripStart.atTime(13, 0),
        tripStart.atTime(15, 0),
        2,
        new BigDecimal("80"));
    saveNode(
        trip,
        "田子坊漫步",
        "田子坊",
        31.2086,
        121.4692,
        Enums.NodeType.ATTRACTION,
        tripStart.plusDays(1).atTime(10, 0),
        tripStart.plusDays(1).atTime(12, 0),
        3,
        new BigDecimal("60"));

    log.info(
        "seeded social bundle: group {} members {},{}; trip {} with 3 nodes",
        g.getId(),
        ma.getId(),
        mb.getId(),
        trip.getId());
  }

  private void seedNationwideTrips(TravelGroup group) {
    LocalDate today = LocalDate.now();
    for (NationwideTripData data : nationwideTrips()) {
      LocalDate start = today.plusDays(data.startOffset());
      Trip trip = new Trip();
      trip.setGroup(group);
      trip.setTitle(data.title());
      trip.setStatus(data.status());
      trip.setStartDate(start);
      trip.setEndDate(start.plusDays(2));
      trip.setTotalBudget(data.budget());
      trip = trips.save(trip);
      for (NationwideNodeData node : data.nodes()) {
        saveNode(
            trip,
            node.name(),
            node.placeName(),
            node.latitude(),
            node.longitude(),
            node.type(),
            start.plusDays(node.dayOffset()).atTime(node.startHour(), 0),
            start.plusDays(node.dayOffset()).atTime(node.endHour(), 0),
            node.sequence(),
            node.cost());
      }
    }
  }

  private List<NationwideTripData> nationwideTrips() {
    return List.of(
        new NationwideTripData(
            "北京经典三日",
            Enums.TripStatus.PLANNED,
            14,
            new BigDecimal("2600"),
            List.of(
                node("故宫晨游", "故宫", 39.9163, 116.3972, Enums.NodeType.ATTRACTION, 0, 9, 11, 1, "80"),
                node("老北京午餐", "王府井", 39.9142, 116.417, Enums.NodeType.MEAL, 0, 12, 13, 2, "120"),
                node(
                    "长城日落",
                    "八达岭长城",
                    40.354,
                    116.014,
                    Enums.NodeType.ATTRACTION,
                    1,
                    9,
                    16,
                    3,
                    "120"))),
        new NationwideTripData(
            "上海摩登漫游",
            Enums.TripStatus.ONGOING,
            24,
            new BigDecimal("2200"),
            List.of(
                node("外滩晨光", "外滩", 31.2304, 121.4737, Enums.NodeType.ATTRACTION, 0, 9, 11, 1, "0"),
                node("本帮午餐", "豫园", 31.2271, 121.492, Enums.NodeType.MEAL, 0, 12, 13, 2, "100"),
                node(
                    "陆家嘴夜景",
                    "东方明珠",
                    31.2397,
                    121.4998,
                    Enums.NodeType.ATTRACTION,
                    1,
                    18,
                    20,
                    3,
                    "180"))),
        new NationwideTripData(
            "天津古今三日",
            Enums.TripStatus.DRAFT,
            34,
            new BigDecimal("1600"),
            List.of(
                node(
                    "津门晨景",
                    "天津之眼",
                    39.1579,
                    117.1875,
                    Enums.NodeType.ATTRACTION,
                    0,
                    9,
                    11,
                    1,
                    "80"),
                node("狗不理午餐", "古文化街", 39.151, 117.195, Enums.NodeType.MEAL, 0, 12, 13, 2, "100"),
                node(
                    "五大道漫步",
                    "五大道",
                    39.113,
                    117.205,
                    Enums.NodeType.ATTRACTION,
                    1,
                    10,
                    12,
                    3,
                    "0"))),
        new NationwideTripData(
            "重庆山城夜色",
            Enums.TripStatus.PLANNED,
            44,
            new BigDecimal("1900"),
            List.of(
                node("洪崖洞打卡", "洪崖洞", 29.563, 106.598, Enums.NodeType.ATTRACTION, 0, 10, 12, 1, "0"),
                node("重庆火锅", "解放碑", 29.563, 106.553, Enums.NodeType.MEAL, 0, 13, 15, 2, "180"),
                node(
                    "长江索道",
                    "长江索道",
                    29.558,
                    106.584,
                    Enums.NodeType.ATTRACTION,
                    1,
                    15,
                    17,
                    3,
                    "40"))),
        new NationwideTripData(
            "广州岭南美食",
            Enums.TripStatus.COMPLETED,
            54,
            new BigDecimal("2100"),
            List.of(
                node("陈家祠", "陈家祠", 23.125, 113.253, Enums.NodeType.ATTRACTION, 0, 9, 11, 1, "10"),
                node("早茶时光", "上下九步行街", 23.115, 113.243, Enums.NodeType.MEAL, 0, 12, 14, 2, "160"),
                node(
                    "珠江夜游", "珠江", 23.108, 113.27, Enums.NodeType.ATTRACTION, 1, 19, 21, 3, "120"))),
        new NationwideTripData(
            "杭州西湖诗意",
            Enums.TripStatus.ONGOING,
            64,
            new BigDecimal("2300"),
            List.of(
                node("断桥晨雾", "断桥残雪", 30.259, 120.148, Enums.NodeType.ATTRACTION, 0, 8, 10, 1, "0"),
                node("龙井茶席", "龙井村", 30.226, 120.13, Enums.NodeType.MEAL, 0, 12, 14, 2, "180"),
                node(
                    "雷峰夕照", "雷峰塔", 30.231, 120.15, Enums.NodeType.ATTRACTION, 1, 17, 19, 3, "40"))),
        new NationwideTripData(
            "南京六朝烟雨",
            Enums.TripStatus.PLANNED,
            74,
            new BigDecimal("1800"),
            List.of(
                node("中山陵", "中山陵", 32.06, 118.849, Enums.NodeType.ATTRACTION, 0, 9, 12, 1, "0"),
                node("秦淮风味", "夫子庙", 32.021, 118.788, Enums.NodeType.MEAL, 0, 13, 14, 2, "120"),
                node(
                    "明城墙夜游",
                    "中华门",
                    32.011,
                    118.768,
                    Enums.NodeType.ATTRACTION,
                    1,
                    17,
                    19,
                    3,
                    "50"))),
        new NationwideTripData(
            "成都慢生活三日",
            Enums.TripStatus.DRAFT,
            84,
            new BigDecimal("2000"),
            List.of(
                node(
                    "熊猫基地",
                    "成都大熊猫繁育研究基地",
                    30.733,
                    104.145,
                    Enums.NodeType.ATTRACTION,
                    0,
                    9,
                    12,
                    1,
                    "55"),
                node("川味午餐", "宽窄巷子", 30.671, 104.052, Enums.NodeType.MEAL, 0, 13, 14, 2, "120"),
                node("锦里夜游", "锦里", 30.648, 104.049, Enums.NodeType.ATTRACTION, 1, 18, 20, 3, "0"))),
        new NationwideTripData(
            "西安古都寻踪",
            Enums.TripStatus.PLANNED,
            94,
            new BigDecimal("2200"),
            List.of(
                node(
                    "兵马俑",
                    "秦始皇兵马俑博物馆",
                    34.385,
                    109.278,
                    Enums.NodeType.ATTRACTION,
                    0,
                    9,
                    12,
                    1,
                    "120"),
                node("回坊小吃", "回民街", 34.263, 108.941, Enums.NodeType.MEAL, 0, 13, 15, 2, "100"),
                node(
                    "大雁塔夜景",
                    "大雁塔",
                    34.22,
                    108.963,
                    Enums.NodeType.ATTRACTION,
                    1,
                    19,
                    21,
                    3,
                    "40"))),
        new NationwideTripData(
            "武汉江城漫步",
            Enums.TripStatus.COMPLETED,
            104,
            new BigDecimal("1700"),
            List.of(
                node("黄鹤楼", "黄鹤楼", 30.544, 114.29, Enums.NodeType.ATTRACTION, 0, 9, 11, 1, "70"),
                node("热干面早餐", "户部巷", 30.541, 114.294, Enums.NodeType.MEAL, 0, 12, 13, 2, "35"),
                node("东湖骑行", "东湖风景区", 30.55, 114.4, Enums.NodeType.OTHER, 1, 10, 13, 3, "80"))),
        new NationwideTripData(
            "长沙烟火之旅",
            Enums.TripStatus.ONGOING,
            114,
            new BigDecimal("1500"),
            List.of(
                node("岳麓书院", "岳麓书院", 28.185, 112.946, Enums.NodeType.ATTRACTION, 0, 9, 11, 1, "40"),
                node("臭豆腐午餐", "坡子街", 28.192, 112.974, Enums.NodeType.MEAL, 0, 12, 14, 2, "80"),
                node("橘子洲头", "橘子洲", 28.177, 112.96, Enums.NodeType.ATTRACTION, 1, 16, 18, 3, "0"))),
        new NationwideTripData(
            "洛阳牡丹古韵",
            Enums.TripStatus.PLANNED,
            124,
            new BigDecimal("1900"),
            List.of(
                node("龙门石窟", "龙门石窟", 34.56, 112.484, Enums.NodeType.ATTRACTION, 0, 9, 12, 1, "90"),
                node("洛阳水席", "老城十字街", 34.68, 112.47, Enums.NodeType.MEAL, 0, 13, 15, 2, "130"),
                node("白马寺", "白马寺", 34.72, 112.64, Enums.NodeType.ATTRACTION, 1, 9, 11, 3, "35"))),
        new NationwideTripData(
            "青岛海风假期",
            Enums.TripStatus.DRAFT,
            134,
            new BigDecimal("2100"),
            List.of(
                node("栈桥日出", "栈桥", 36.061, 120.32, Enums.NodeType.ATTRACTION, 0, 8, 10, 1, "0"),
                node("海鲜午餐", "八大关", 36.05, 120.35, Enums.NodeType.MEAL, 0, 12, 14, 2, "220"),
                node(
                    "崂山看海", "崂山", 36.193, 120.602, Enums.NodeType.ATTRACTION, 1, 9, 15, 3, "130"))),
        new NationwideTripData(
            "大连滨海风光",
            Enums.TripStatus.PLANNED,
            144,
            new BigDecimal("2000"),
            List.of(
                node("星海广场", "星海广场", 38.878, 121.593, Enums.NodeType.ATTRACTION, 0, 9, 11, 1, "0"),
                node("海鲜烧烤", "渔人码头", 38.87, 121.67, Enums.NodeType.MEAL, 0, 12, 14, 2, "180"),
                node(
                    "老虎滩",
                    "老虎滩海洋公园",
                    38.872,
                    121.682,
                    Enums.NodeType.ATTRACTION,
                    1,
                    9,
                    13,
                    3,
                    "150"))),
        new NationwideTripData(
            "哈尔滨冰雪之约",
            Enums.TripStatus.COMPLETED,
            154,
            new BigDecimal("2800"),
            List.of(
                node("中央大街", "中央大街", 45.773, 126.617, Enums.NodeType.ATTRACTION, 0, 9, 11, 1, "0"),
                node("俄式午餐", "索菲亚广场", 45.765, 126.622, Enums.NodeType.MEAL, 0, 12, 14, 2, "150"),
                node(
                    "冰雪大世界",
                    "哈尔滨冰雪大世界",
                    45.808,
                    126.608,
                    Enums.NodeType.ATTRACTION,
                    1,
                    16,
                    21,
                    3,
                    "300"))),
        new NationwideTripData(
            "长春汽车与春光",
            Enums.TripStatus.DRAFT,
            164,
            new BigDecimal("1500"),
            List.of(
                node(
                    "伪满皇宫",
                    "伪满皇宫博物院",
                    43.91,
                    125.338,
                    Enums.NodeType.ATTRACTION,
                    0,
                    9,
                    12,
                    1,
                    "70"),
                node("东北菜午餐", "桂林路", 43.88, 125.324, Enums.NodeType.MEAL, 0, 13, 14, 2, "100"),
                node(
                    "净月潭",
                    "净月潭国家森林公园",
                    43.8,
                    125.45,
                    Enums.NodeType.ATTRACTION,
                    1,
                    9,
                    13,
                    3,
                    "50"))),
        new NationwideTripData(
            "石家庄周边古迹",
            Enums.TripStatus.PLANNED,
            174,
            new BigDecimal("1400"),
            List.of(
                node("正定古城", "正定古城", 38.147, 114.57, Enums.NodeType.ATTRACTION, 0, 9, 12, 1, "0"),
                node("驴肉火烧", "正定南城门", 38.14, 114.57, Enums.NodeType.MEAL, 0, 12, 13, 2, "60"),
                node("隆兴寺", "隆兴寺", 38.149, 114.57, Enums.NodeType.ATTRACTION, 1, 9, 12, 3, "50"))),
        new NationwideTripData(
            "太原晋韵行",
            Enums.TripStatus.ONGOING,
            184,
            new BigDecimal("1600"),
            List.of(
                node("晋祠古建", "晋祠", 37.713, 112.445, Enums.NodeType.ATTRACTION, 0, 9, 12, 1, "80"),
                node("刀削面", "柳巷", 37.866, 112.568, Enums.NodeType.MEAL, 0, 13, 14, 2, "45"),
                node(
                    "古县城夜游",
                    "太原古县城",
                    37.742,
                    112.47,
                    Enums.NodeType.ATTRACTION,
                    1,
                    17,
                    20,
                    3,
                    "30"))),
        new NationwideTripData(
            "黄山云海三日",
            Enums.TripStatus.PLANNED,
            194,
            new BigDecimal("2400"),
            List.of(
                node(
                    "黄山日出",
                    "黄山风景区",
                    30.134,
                    118.166,
                    Enums.NodeType.ATTRACTION,
                    0,
                    7,
                    11,
                    1,
                    "190"),
                node("徽州午餐", "屯溪老街", 29.715, 118.315, Enums.NodeType.MEAL, 0, 13, 15, 2, "120"),
                node(
                    "宏村写生", "宏村", 30.011, 117.989, Enums.NodeType.ATTRACTION, 1, 9, 12, 3, "104"))),
        new NationwideTripData(
            "厦门海岛时光",
            Enums.TripStatus.DRAFT,
            204,
            new BigDecimal("2200"),
            List.of(
                node("鼓浪屿漫步", "鼓浪屿", 24.448, 118.065, Enums.NodeType.ATTRACTION, 0, 9, 12, 1, "35"),
                node("沙茶面", "中山路", 24.45, 118.083, Enums.NodeType.MEAL, 0, 13, 14, 2, "50"),
                node("环岛骑行", "环岛路", 24.43, 118.15, Enums.NodeType.OTHER, 1, 9, 12, 3, "80"))),
        new NationwideTripData(
            "南昌滕王阁记",
            Enums.TripStatus.COMPLETED,
            214,
            new BigDecimal("1300"),
            List.of(
                node("滕王阁", "滕王阁", 28.683, 115.884, Enums.NodeType.ATTRACTION, 0, 9, 11, 1, "50"),
                node("瓦罐汤", "万寿宫", 28.677, 115.888, Enums.NodeType.MEAL, 0, 12, 13, 2, "60"),
                node(
                    "八一广场", "八一广场", 28.676, 115.91, Enums.NodeType.ATTRACTION, 1, 17, 19, 3, "0"))),
        new NationwideTripData(
            "大理苍山洱海",
            Enums.TripStatus.PLANNED,
            224,
            new BigDecimal("2300"),
            List.of(
                node("洱海日出", "洱海", 25.79, 100.19, Enums.NodeType.ATTRACTION, 0, 8, 10, 1, "0"),
                node("白族午餐", "大理古城", 25.694, 100.16, Enums.NodeType.MEAL, 0, 12, 14, 2, "120"),
                node("苍山索道", "苍山", 25.68, 100.1, Enums.NodeType.ATTRACTION, 1, 9, 14, 3, "160"))),
        new NationwideTripData(
            "贵阳山地清凉",
            Enums.TripStatus.ONGOING,
            234,
            new BigDecimal("1700"),
            List.of(
                node("青岩古镇", "青岩古镇", 26.334, 106.65, Enums.NodeType.ATTRACTION, 0, 9, 12, 1, "60"),
                node("酸汤鱼", "青云市集", 26.57, 106.71, Enums.NodeType.MEAL, 0, 13, 15, 2, "140"),
                node(
                    "黔灵山公园",
                    "黔灵山公园",
                    26.595,
                    106.696,
                    Enums.NodeType.ATTRACTION,
                    1,
                    9,
                    12,
                    3,
                    "5"))),
        new NationwideTripData(
            "敦煌丝路寻踪",
            Enums.TripStatus.PLANNED,
            244,
            new BigDecimal("2600"),
            List.of(
                node("莫高窟", "莫高窟", 40.04, 94.804, Enums.NodeType.ATTRACTION, 0, 9, 13, 1, "238"),
                node("驴肉黄面", "沙洲夜市", 40.143, 94.662, Enums.NodeType.MEAL, 0, 14, 15, 2, "90"),
                node(
                    "鸣沙山月牙泉",
                    "鸣沙山月牙泉",
                    40.087,
                    94.662,
                    Enums.NodeType.ATTRACTION,
                    1,
                    16,
                    20,
                    3,
                    "110"))),
        new NationwideTripData(
            "西宁高原初见",
            Enums.TripStatus.DRAFT,
            254,
            new BigDecimal("1800"),
            List.of(
                node("塔尔寺", "塔尔寺", 36.48, 101.57, Enums.NodeType.ATTRACTION, 0, 9, 12, 1, "70"),
                node("手抓羊肉", "莫家街", 36.622, 101.78, Enums.NodeType.MEAL, 0, 13, 14, 2, "100"),
                node(
                    "东关清真大寺",
                    "东关清真大寺",
                    36.62,
                    101.78,
                    Enums.NodeType.ATTRACTION,
                    1,
                    10,
                    12,
                    3,
                    "25"))),
        new NationwideTripData(
            "呼和浩特草原行",
            Enums.TripStatus.PLANNED,
            264,
            new BigDecimal("1900"),
            List.of(
                node("大召寺", "大召寺", 40.792, 111.66, Enums.NodeType.ATTRACTION, 0, 9, 11, 1, "35"),
                node("奶茶午餐", "塞上老街", 40.792, 111.66, Enums.NodeType.MEAL, 0, 12, 14, 2, "100"),
                node(
                    "希拉穆仁草原",
                    "希拉穆仁草原",
                    41.14,
                    111.18,
                    Enums.NodeType.ATTRACTION,
                    1,
                    9,
                    15,
                    3,
                    "180"))),
        new NationwideTripData(
            "桂林山水画卷",
            Enums.TripStatus.COMPLETED,
            274,
            new BigDecimal("2100"),
            List.of(
                node("漓江竹筏", "漓江", 25.275, 110.29, Enums.NodeType.ATTRACTION, 0, 9, 13, 1, "216"),
                node("桂林米粉", "正阳步行街", 25.28, 110.295, Enums.NodeType.MEAL, 0, 14, 15, 2, "35"),
                node("象鼻山", "象鼻山", 25.27, 110.3, Enums.NodeType.ATTRACTION, 1, 16, 18, 3, "75"))),
        new NationwideTripData(
            "拉萨高原朝圣",
            Enums.TripStatus.PLANNED,
            284,
            new BigDecimal("3000"),
            List.of(
                node("布达拉宫", "布达拉宫", 29.657, 91.117, Enums.NodeType.ATTRACTION, 0, 9, 12, 1, "200"),
                node("藏式午餐", "八廓街", 29.65, 91.13, Enums.NodeType.MEAL, 0, 13, 15, 2, "120"),
                node("大昭寺", "大昭寺", 29.65, 91.13, Enums.NodeType.ATTRACTION, 1, 9, 11, 3, "85"))),
        new NationwideTripData(
            "银川塞上风光",
            Enums.TripStatus.DRAFT,
            294,
            new BigDecimal("1600"),
            List.of(
                node("西夏王陵", "西夏王陵", 38.487, 105.985, Enums.NodeType.ATTRACTION, 0, 9, 12, 1, "75"),
                node("羊杂碎", "鼓楼步行街", 38.469, 106.273, Enums.NodeType.MEAL, 0, 13, 14, 2, "60"),
                node("沙湖泛舟", "沙湖", 38.75, 106.35, Enums.NodeType.ATTRACTION, 1, 9, 14, 3, "120"))),
        new NationwideTripData(
            "乌鲁木齐天山之旅",
            Enums.TripStatus.ONGOING,
            304,
            new BigDecimal("2500"),
            List.of(
                node("天山天池", "天山天池", 43.88, 88.12, Enums.NodeType.ATTRACTION, 0, 9, 14, 1, "155"),
                node("新疆大盘鸡", "国际大巴扎", 43.79, 87.61, Enums.NodeType.MEAL, 0, 17, 19, 2, "150"),
                node("红山公园", "红山公园", 43.81, 87.61, Enums.NodeType.ATTRACTION, 1, 9, 11, 3, "0"))),
        new NationwideTripData(
            "三亚椰风海岛",
            Enums.TripStatus.PLANNED,
            314,
            new BigDecimal("2800"),
            List.of(
                node("亚龙湾", "亚龙湾", 18.23, 109.63, Enums.NodeType.ATTRACTION, 0, 9, 12, 1, "100"),
                node("海鲜午餐", "第一市场", 18.25, 109.51, Enums.NodeType.MEAL, 0, 13, 15, 2, "220"),
                node("天涯海角", "天涯海角", 18.3, 109.37, Enums.NodeType.ATTRACTION, 1, 9, 12, 3, "80"))),
        new NationwideTripData(
            "台北城市漫游",
            Enums.TripStatus.DRAFT,
            324,
            new BigDecimal("2600"),
            List.of(
                node(
                    "台北故宫",
                    "台北故宫博物院",
                    25.102,
                    121.548,
                    Enums.NodeType.ATTRACTION,
                    0,
                    9,
                    12,
                    1,
                    "350"),
                node("小笼包午餐", "鼎泰丰信义店", 25.033, 121.565, Enums.NodeType.MEAL, 0, 13, 14, 2, "180"),
                node(
                    "台北101",
                    "台北101",
                    25.034,
                    121.564,
                    Enums.NodeType.ATTRACTION,
                    1,
                    17,
                    20,
                    3,
                    "600"))),
        new NationwideTripData(
            "香港城市观景",
            Enums.TripStatus.COMPLETED,
            334,
            new BigDecimal("3000"),
            List.of(
                node("维港晨景", "维多利亚港", 22.294, 114.17, Enums.NodeType.ATTRACTION, 0, 9, 11, 1, "0"),
                node("港式午茶", "尖沙咀", 22.298, 114.172, Enums.NodeType.MEAL, 0, 12, 14, 2, "160"),
                node(
                    "太平山顶",
                    "太平山顶",
                    22.276,
                    114.145,
                    Enums.NodeType.ATTRACTION,
                    1,
                    17,
                    20,
                    3,
                    "100"))),
        new NationwideTripData(
            "澳门历史街巷",
            Enums.TripStatus.PLANNED,
            344,
            new BigDecimal("2200"),
            List.of(
                node("大三巴牌坊", "大三巴牌坊", 22.197, 113.54, Enums.NodeType.ATTRACTION, 0, 9, 11, 1, "0"),
                node("葡式午餐", "议事亭前地", 22.193, 113.54, Enums.NodeType.MEAL, 0, 12, 14, 2, "180"),
                node(
                    "澳门塔",
                    "澳门旅游塔",
                    22.189,
                    113.537,
                    Enums.NodeType.ATTRACTION,
                    1,
                    17,
                    20,
                    3,
                    "165"))));
  }

  private NationwideNodeData node(
      String name,
      String placeName,
      double latitude,
      double longitude,
      Enums.NodeType type,
      int dayOffset,
      int startHour,
      int endHour,
      int sequence,
      String cost) {
    return new NationwideNodeData(
        name,
        placeName,
        latitude,
        longitude,
        type,
        dayOffset,
        startHour,
        endHour,
        sequence,
        new BigDecimal(cost));
  }

  private record NationwideTripData(
      String title,
      Enums.TripStatus status,
      int startOffset,
      BigDecimal budget,
      List<NationwideNodeData> nodes) {}

  private record NationwideNodeData(
      String name,
      String placeName,
      double latitude,
      double longitude,
      Enums.NodeType type,
      int dayOffset,
      int startHour,
      int endHour,
      int sequence,
      BigDecimal cost) {}

  private User ensureUser(String name, String email, String phone) {
    return users
        .findByEmail(email)
        .orElseGet(
            () -> users.save(new User(name, email, passwordEncoder.encode("password123"), phone)));
  }

  private void ensureFriendship(User requester, User addressee, Enums.FriendshipStatus status) {
    if (friendships
            .findByRequesterIdAndAddresseeId(requester.getId(), addressee.getId())
            .isPresent()
        || friendships
            .findByRequesterIdAndAddresseeId(addressee.getId(), requester.getId())
            .isPresent()) {
      return;
    }
    Friendship f = new Friendship(requester, addressee);
    f.setStatus(status);
    friendships.save(f);
  }

  private void saveNode(
      Trip trip,
      String name,
      String placeName,
      double latitude,
      double longitude,
      Enums.NodeType type,
      LocalDateTime start,
      LocalDateTime end,
      int sequence,
      BigDecimal cost) {
    ItineraryNode node = new ItineraryNode();
    node.setTrip(trip);
    node.setName(name);
    node.setPlaceName(placeName);
    node.setLatitude(latitude);
    node.setLongitude(longitude);
    node.setNodeType(type);
    node.setPlannedStart(start);
    node.setPlannedEnd(end);
    node.setSequenceOrder(sequence);
    node.setCost(cost);
    node.setStatus(Enums.NodeStatus.PLANNED);
    nodes.save(node);
  }
}
