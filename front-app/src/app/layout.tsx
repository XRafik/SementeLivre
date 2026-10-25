import type { Metadata, Viewport } from 'next';
import './globals.css';
import { ToastProvider } from '@/components/feedback/Toast';
import { SplashScreen } from '@/components/shared/SplashScreen';

export const metadata: Metadata = {
  title: 'Semente Livre',
  description: 'Gestão de banco de sementes crioulas para produtores rurais familiares',
  manifest: '/manifest.json',
  appleWebApp: {
    capable: true,
    statusBarStyle: 'default',
    title: 'Semente Livre',
  },
};

export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
  themeColor: '#2f9e41',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="pt-BR">
      <head>
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="anonymous" />
        <link
          href="https://fonts.googleapis.com/css2?family=Poppins:wght@400;500;600;700&display=swap"
          rel="stylesheet"
        />
      </head>
      <body>
        <ToastProvider>{children}</ToastProvider>
        <SplashScreen />
      </body>
    </html>
  );
}
