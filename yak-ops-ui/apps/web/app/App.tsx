import { ToastProvider } from "@yak-ops/yak-ui";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter } from "react-router-dom";

import { AuthProvider } from "@/context/auth-context";
import { WorkspaceProvider } from "@/context/workspace-context";

import AppRouter from "./router/AppRouter";

const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

export default function App() {
  return (
    <ToastProvider>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <AuthProvider>
            <WorkspaceProvider>
              <AppRouter />
            </WorkspaceProvider>
          </AuthProvider>
        </BrowserRouter>
      </QueryClientProvider>
    </ToastProvider>
  );
}
