import { Construction } from "lucide-react";
import { PageHeader, Card } from "../components/ui";

export function PlaceholderPage({ title, eyebrow = "PASS 2" }: { title: string; eyebrow?: string }) {
  return <><PageHeader eyebrow={eyebrow} title={title} description="这个模块正在接入动态行程闭环，下一版会带来完整的协作体验。" /><Card className="flex min-h-[360px] flex-col items-center justify-center p-8 text-center"><div className="grid h-16 w-16 place-items-center rounded-2xl bg-coral/10 text-coral"><Construction size={28} /></div><h2 className="mt-5 font-display text-xl font-bold text-ink">即将上线</h2><p className="mt-2 max-w-sm text-sm leading-6 text-ink-soft">先把路线走顺，再把更多人的想法接进来。该模块将在 pass 2 开放。</p></Card></>;
}
