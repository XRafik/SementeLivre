import Link from "next/link";
import { Sprout } from "lucide-react";

export default function PublicHeader() {
  return (
    <header className="sticky top-0 z-50 bg-white/90 backdrop-blur-md border-b border-green-100 shadow-sm">
      <div className="max-w-7xl mx-auto px-6 h-16 flex items-center justify-between gap-4">
        {/* Brand */}
        <Link href="/" className="flex items-center gap-2.5 group">
          <div className="w-8 h-8 rounded-lg bg-green-600 flex items-center justify-center group-hover:bg-green-700 transition-colors">
            <Sprout className="w-4 h-4 text-white" />
          </div>
          <span className="font-bold text-green-900 text-lg tracking-tight">
            Semente Livre
          </span>
        </Link>

        {/* Nav links */}
        <nav className="hidden sm:flex items-center gap-6 text-sm font-medium text-gray-600">
          <Link href="/" className="hover:text-green-700 transition-colors">
            Início
          </Link>
          <Link href="/#sementes" className="hover:text-green-700 transition-colors">
            Sementes
          </Link>
          <Link href="/#sobre" className="hover:text-green-700 transition-colors">
            Sobre a Rede
          </Link>
        </nav>
      </div>
    </header>
  );
}
