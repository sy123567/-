import { AlertCircle, ArrowLeft, Inbox, LoaderCircle, RefreshCw } from "lucide-react";
import { Link } from "react-router-dom";
import { apiBase } from "../api/client";
import { Button, Card } from "./ui";

export function LoadingState({ label = "正在整理你的路线…" }: { label?: string }) {
  return (
    <div className="flex min-h-[280px] items-center justify-center rounded-card border border-dashed border-slate-200 bg-white/70 p-8 text-center">
      <div>
        <LoaderCircle className="mx-auto animate-spin text-coral motion-reduce:animate-none" size={30} aria-hidden="true" />
        <p className="mt-4 text-sm font-medium text-ink-soft">{label}</p>
      </div>
    </div>
  );
}

export function ErrorState({ title = "连不上后端服务", message, onRetry }: { title?: string; message?: string; onRetry?: () => void }) {
  return (
    <Card className="flex min-h-[280px] items-center justify-center border border-coral/15 bg-white p-8 text-center">
      <div className="max-w-md">
        <div className="mx-auto grid h-14 w-14 place-items-center rounded-2xl bg-coral/10 text-coral">
          <AlertCircle size={27} aria-hidden="true" />
        </div>
        <h2 className="mt-5 font-display text-xl font-bold text-ink">{title}</h2>
        <p className="mt-2 text-sm leading-6 text-ink-soft">
          {message ?? `暂时无法访问 ${apiBase}。请确认后端服务已启动，或将 VITE_USE_MOCKS=true 后重启前端。`}
        </p>
        <div className="mt-5 flex flex-wrap justify-center gap-3">
          {onRetry && (
            <Button variant="secondary" onClick={onRetry} className="inline-flex items-center gap-2">
              <RefreshCw size={15} />重试
            </Button>
          )}
          <Link to="/">
            <Button variant="ghost" className="inline-flex items-center gap-2">
              <ArrowLeft size={15} />返回首页
            </Button>
          </Link>
        </div>
      </div>
    </Card>
  );
}

export function EmptyState({ title = "这里还没有内容", message = "换个筛选条件，或者稍后再来看看。" }: { title?: string; message?: string }) {
  return (
    <div className="flex min-h-[220px] items-center justify-center rounded-card border border-dashed border-slate-200 bg-white/70 p-8 text-center">
      <div>
        <Inbox className="mx-auto text-ink-soft" size={28} aria-hidden="true" />
        <h2 className="mt-4 font-display text-lg font-bold text-ink">{title}</h2>
        <p className="mt-2 text-sm text-ink-soft">{message}</p>
      </div>
    </div>
  );
}
