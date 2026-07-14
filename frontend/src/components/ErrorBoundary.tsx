import { Component, type ErrorInfo, type ReactNode } from "react";
import { RefreshCw, Route } from "lucide-react";
import { Button, Card } from "./ui";

type Props = { children: ReactNode };
type State = { hasError: boolean };

export class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false };

  static getDerivedStateFromError(): State {
    return { hasError: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error("Unhandled render error", error, info);
  }

  handleRetry = () => {
    window.location.reload();
  };

  render() {
    if (!this.state.hasError) return this.props.children;
    return (
      <main className="grid min-h-screen place-items-center bg-paper px-5 py-12 text-ink">
        <Card className="relative w-full max-w-xl overflow-hidden p-8 text-center shadow-soft md:p-12">
          <div className="absolute -right-16 -top-16 h-40 w-40 rounded-full bg-coral/10" />
          <div className="relative mx-auto grid h-16 w-16 place-items-center rounded-2xl bg-ink text-coral">
            <Route size={30} aria-hidden="true" />
          </div>
          <p className="relative mt-6 font-mono text-[10px] tracking-[0.2em] text-coral">TRIP ADAPTIVE / RECOVERY</p>
          <h1 className="relative mt-3 font-display text-3xl font-bold">页面出错了</h1>
          <p className="relative mx-auto mt-3 max-w-sm text-sm leading-6 text-ink-soft">
            这条路线暂时偏离了轨道。刷新页面试试；如果问题持续，可以检查后端服务或切换到 Mock 数据。
          </p>
          <Button onClick={this.handleRetry} className="relative mt-7 inline-flex items-center gap-2">
            <RefreshCw size={16} />重试
          </Button>
        </Card>
      </main>
    );
  }
}
