'use client';

import React from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { LayoutDashboard, Package, ShoppingCart, MapPin, User } from 'lucide-react';
import styles from './BottomNavigation.module.css';

const navItems = [
  { href: '/dashboard', label: 'Início', Icon: LayoutDashboard },
  { href: '/produtos', label: 'Produtos', Icon: Package },
  { href: '/pedidos', label: 'Pedidos', Icon: ShoppingCart },
  { href: '/propriedades', label: 'Propriedades', Icon: MapPin },
  { href: '/perfil', label: 'Perfil', Icon: User },
];

export function BottomNavigation() {
  const pathname = usePathname();

  return (
    <nav className={styles.nav} aria-label="Navegação principal">
      <div className={styles.inner}>
        {navItems.map((item) => {
          const isActive = pathname === item.href || pathname.startsWith(item.href + '/');
          return (
            <Link
              key={item.href}
              href={item.href}
              className={`${styles.item} ${isActive ? styles.active : ''}`}
              aria-current={isActive ? 'page' : undefined}
            >
              <span className={styles.iconWrap} aria-hidden="true">
                <item.Icon
                  size={20}
                  strokeWidth={isActive ? 2.5 : 1.75}
                  className={styles.iconSvg}
                />
              </span>
              <span className={styles.label}>{item.label}</span>
            </Link>
          );
        })}
      </div>
    </nav>
  );
}
