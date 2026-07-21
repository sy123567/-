import { Heart, Search, SlidersHorizontal, Star } from "lucide-react";
import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "../api/client";
import { Badge, Button, Card, Input, PageHeader } from "../components/ui";
import { ImageFallback, Modal, Toast } from "../components/pass2";
import { EmptyState, ErrorState, LoadingState } from "../components/AsyncState";

const categories = ["全部", "城市漫游", "美食探索", "自然风光", "疗愈放空"];
const themeOptions = ["城市漫游", "美食探索", "自然风光", "疗愈放空"];

function GuidePublishModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [title, setTitle] = useState("");
  const [city, setCity] = useState("");
  const [days, setDays] = useState("2");
  const [theme, setTheme] = useState(themeOptions[0]);
  const [price, setPrice] = useState("");
  const [cover, setCover] = useState("");
  const [description, setDescription] = useState("");
  const [tags, setTags] = useState("");
  const [toast, setToast] = useState("");
  const reset = () => { setTitle(""); setCity(""); setDays("2"); setTheme(themeOptions[0]); setPrice(""); setCover(""); setDescription(""); setTags(""); };
  const publish = useMutation({
    mutationFn: () => api.createGuide({
      title: title.trim(),
      city: city.trim(),
      days: Number(days) || 1,
      theme,
      price: Number(price) || 0,
      cover: cover.trim() || undefined,
      description: description.trim(),
      tags: tags.split(/[,，\s]+/).map((t) => t.trim()).filter(Boolean),
    }),
    onSuccess: () => { void queryClient.invalidateQueries({ queryKey: ["guides"] }); reset(); onClose(); setToast("攻略已发布"); },
    onError: (error) => setToast(error instanceof Error ? error.message : "发布失败"),
  });
  const valid = title.trim() && city.trim() && description.trim();
  return <>{toast && <Toast message={toast} onClose={() => setToast("")} />}<Modal open={open} title="发布攻略" onClose={onClose}><div className="space-y-4"><div><label className="text-xs font-semibold text-ink-soft">标题</label><Input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="给这条路线起个名字" /></div><div className="grid grid-cols-2 gap-3"><div><label className="text-xs font-semibold text-ink-soft">城市</label><Input value={city} onChange={(e) => setCity(e.target.value)} placeholder="上海" /></div><div><label className="text-xs font-semibold text-ink-soft">天数</label><Input type="number" min={1} value={days} onChange={(e) => setDays(e.target.value)} /></div></div><div className="grid grid-cols-2 gap-3"><div><label className="text-xs font-semibold text-ink-soft">主题</label><select value={theme} onChange={(e) => setTheme(e.target.value)} className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm">{themeOptions.map((t) => <option key={t}>{t}</option>)}</select></div><div><label className="text-xs font-semibold text-ink-soft">人均预算 ¥</label><Input type="number" min={0} value={price} onChange={(e) => setPrice(e.target.value)} placeholder="980" /></div></div><div><label className="text-xs font-semibold text-ink-soft">封面图链接（可选）</label><Input value={cover} onChange={(e) => setCover(e.target.value)} placeholder="https://…" /></div><div><label className="text-xs font-semibold text-ink-soft">标签（用逗号或空格分隔）</label><Input value={tags} onChange={(e) => setTags(e.target.value)} placeholder="Citywalk 咖啡 拍照" /></div><div><label className="text-xs font-semibold text-ink-soft">简介</label><textarea value={description} onChange={(e) => setDescription(e.target.value)} className="mt-1 min-h-24 w-full rounded-lg border border-slate-200 p-3 text-sm leading-6 focus:border-sky focus:outline-none" placeholder="这条路线的亮点是…" /></div><div className="flex justify-end gap-2 pt-2"><Button variant="ghost" onClick={onClose}>取消</Button><Button disabled={!valid || publish.isPending} onClick={() => publish.mutate()}>发布</Button></div></div></Modal></>;
}

export function GuidesPage() {
  const { data, isLoading, isError, error, refetch } = useQuery({ queryKey: ["guides"], queryFn: api.guides });
  const [category, setCategory] = useState("全部");
  const [query, setQuery] = useState("");
  const [publishOpen, setPublishOpen] = useState(false);
  const filtered = useMemo(() => (data ?? []).filter((guide) => (category === "全部" || guide.theme === category) && `${guide.title}${guide.city}${guide.tags.join("")}`.toLowerCase().includes(query.toLowerCase())), [data, category, query]);
  if (isLoading) return <LoadingState label="正在装载旅伴攻略…" />;
  if (isError) return <ErrorState onRetry={() => void refetch()} message={error instanceof Error ? error.message : undefined} />;
  if (!data) return <EmptyState title="攻略暂时不可用" />;
  if (filtered.length === 0) return <><PageHeader eyebrow="TRAVEL GUIDE COMMUNITY" title="把别人的好路线，变成你的下一站" description="真实旅程、具体建议，还有攻略纳用后留给你们的自由。" /><EmptyState title="没有匹配的攻略" message="试试清空搜索词，或换一个主题分类。" /></>;
  return <><PageHeader eyebrow="TRAVEL GUIDE COMMUNITY" title="把别人的好路线，变成你的下一站" description="真实旅程、具体建议，还有攻略纳用后留给你们的自由。" action={<Button variant="secondary" onClick={() => setPublishOpen(true)} className="flex items-center gap-2"><SlidersHorizontal size={16} />发布攻略</Button>} /><GuidePublishModal open={publishOpen} onClose={() => setPublishOpen(false)} /><Card className="mb-7 p-4 md:p-5"><div className="flex flex-col gap-4 lg:flex-row lg:items-center"><div className="relative flex-1"><Search size={17} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-soft" /><Input className="pl-10" placeholder="搜索城市、主题或攻略名" value={query} onChange={(event) => setQuery(event.target.value)} /></div><div className="flex flex-wrap gap-2">{categories.map((item) => <button key={item} onClick={() => setCategory(item)} className={`rounded-full px-3.5 py-2 text-xs font-semibold transition ${category === item ? "bg-ink text-white" : "bg-paper text-ink-soft hover:bg-ink/5"}`}>{item}</button>)}</div></div><div className="mt-4 flex flex-wrap items-center gap-3 border-t border-slate-100 pt-4 text-xs text-ink-soft"><span>筛选条件</span><select className="rounded-lg border border-slate-200 bg-white px-3 py-2" defaultValue="all"><option value="all">城市：全部</option><option>上海</option><option>成都</option><option>杭州</option></select><select className="rounded-lg border border-slate-200 bg-white px-3 py-2" defaultValue="all"><option value="all">天数：全部</option><option>2天以内</option><option>3天</option><option>4天以上</option></select><select className="rounded-lg border border-slate-200 bg-white px-3 py-2" defaultValue="rating"><option value="rating">评分最高</option><option>收藏最多</option><option>价格最低</option></select></div></Card><div className="mb-4 flex items-end justify-between"><div><p className="text-sm font-semibold text-ink">{filtered.length} 条路线适合你</p><p className="mt-1 text-xs text-ink-soft">优先展示与你最近浏览偏好相近的攻略</p></div><p className="font-mono text-xs text-ink-soft">UPDATED TODAY</p></div><div className="grid gap-5 md:grid-cols-2 xl:grid-cols-3">{filtered.map((guide) => <Card key={guide.id} className="group overflow-hidden"><Link to={`/guides/${guide.id}`} className="block"><div className="relative h-48 overflow-hidden"><ImageFallback src={guide.cover} alt={guide.title} city={guide.city} /><div className="absolute inset-x-0 top-0 flex justify-between p-4"><Badge tone="neutral">{guide.city} · {guide.days}天</Badge><button aria-label={`收藏${guide.title}`} className="grid h-9 w-9 place-items-center rounded-full bg-white/90 text-ink-soft shadow-sm backdrop-blur transition hover:text-coral" onClick={(event) => event.preventDefault()}><Heart size={16} /></button></div></div><div className="p-5"><div className="flex items-center justify-between gap-3"><Badge tone="coral">{guide.theme}</Badge><span className="flex items-center gap-1 text-xs font-semibold text-ink"><Star size={14} className="fill-sun text-sun" />{guide.rating} <span className="font-normal text-ink-soft">({guide.reviews})</span></span></div><h2 className="mt-3 font-display text-lg font-bold leading-7 text-ink">{guide.title}</h2><p className="mt-2 line-clamp-2 text-sm leading-6 text-ink-soft">{guide.description}</p><div className="mt-4 flex flex-wrap gap-1.5">{guide.tags.map((tag) => <span key={tag} className="rounded-md bg-paper px-2 py-1 text-[11px] text-ink-soft">#{tag}</span>)}</div><div className="mt-5 flex items-center justify-between border-t border-slate-100 pt-4"><div><p className="text-[11px] text-ink-soft">预计人均</p><p className="font-mono text-sm font-bold text-ink">¥{guide.price.toLocaleString()}</p></div><Button className="px-3 py-2 text-xs" onClick={(event) => event.preventDefault()}>攻略纳用</Button></div><div className="mt-3 flex items-center gap-2 text-[11px] text-ink-soft"><img src={guide.author.avatar} alt="" className="h-5 w-5 rounded-full" />{guide.author.name} · {guide.saves} 人收藏</div></div></Link></Card>)}</div></>;
}
