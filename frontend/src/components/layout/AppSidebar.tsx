import { Link, NavLink, useLocation } from 'react-router-dom';
import { ChevronsUpDown, LogOut, UserRound } from 'lucide-react';
import { toast } from 'sonner';
import { displayName, useAuth } from '@/auth/auth-context';
import { Logo } from '@/components/app/Logo';
import { RoleBadge } from '@/components/app/RoleBadge';
import { Avatar, AvatarFallback } from '@/components/ui/avatar';
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import {
  Sidebar, SidebarContent, SidebarFooter, SidebarGroup, SidebarGroupContent, SidebarGroupLabel, SidebarHeader,
  SidebarMenu, SidebarMenuButton, SidebarMenuItem, SidebarRail, useSidebar,
} from '@/components/ui/sidebar';
import { ROLE_DESCRIPTIONS } from '@/lib/roles';
import { NAV } from './nav';

export function AppSidebar() {
  const { user, can, logout } = useAuth();
  const { pathname } = useLocation();
  const { isMobile, setOpenMobile } = useSidebar();

  if (!user) return null;

  const sections = NAV
    .map(section => ({ ...section, items: section.items.filter(item => !item.permission || can(item.permission)) }))
    .filter(section => section.items.length > 0);

  return (
    <Sidebar collapsible="icon">
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton size="lg" asChild>
              <Link to="/" onClick={() => setOpenMobile(false)}>
                <Logo textClassName="group-data-[collapsible=icon]:hidden" />
              </Link>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>

      <SidebarContent>
        {sections.map(section => (
          <SidebarGroup key={section.label}>
            <SidebarGroupLabel>{section.label}</SidebarGroupLabel>
            <SidebarGroupContent>
              <SidebarMenu>
                {section.items.map(item => {
                  const active = item.end ? pathname === item.to : pathname.startsWith(item.to);
                  return (
                    <SidebarMenuItem key={item.to}>
                      <SidebarMenuButton asChild isActive={active} tooltip={item.label}>
                        <NavLink to={item.to} end={item.end} onClick={() => setOpenMobile(false)}>
                          <item.icon />
                          <span>{item.label}</span>
                        </NavLink>
                      </SidebarMenuButton>
                    </SidebarMenuItem>
                  );
                })}
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>
        ))}
      </SidebarContent>

      <SidebarFooter>
        <SidebarMenu>
          <SidebarMenuItem>
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <SidebarMenuButton size="lg" className="data-[state=open]:bg-sidebar-accent">
                  <Avatar className="size-8 rounded-lg">
                    <AvatarFallback className="rounded-lg bg-primary/15 font-semibold text-primary uppercase">
                      {displayName(user)[0]}
                    </AvatarFallback>
                  </Avatar>
                  <div className="grid flex-1 text-left text-sm leading-tight">
                    <span className="truncate font-medium">{displayName(user)}</span>
                    <span className="truncate text-xs text-muted-foreground capitalize">{user.role.toLowerCase()}</span>
                  </div>
                  <ChevronsUpDown className="ml-auto size-4" />
                </SidebarMenuButton>
              </DropdownMenuTrigger>
              <DropdownMenuContent className="w-64" side={isMobile ? 'bottom' : 'right'} align="end" sideOffset={4}>
                <DropdownMenuLabel className="font-normal">
                  <div className="flex items-center justify-between gap-2">
                    <span className="truncate font-medium">{displayName(user)}</span>
                    <RoleBadge role={user.role} />
                  </div>
                  {user.fullName && <p className="truncate text-xs text-muted-foreground">{user.email}</p>}
                  <p className="mt-1 text-xs text-muted-foreground">{ROLE_DESCRIPTIONS[user.role]}</p>
                </DropdownMenuLabel>
                <DropdownMenuSeparator />
                <DropdownMenuItem asChild>
                  <Link to="/account" onClick={() => setOpenMobile(false)}><UserRound /> Account</Link>
                </DropdownMenuItem>
                <DropdownMenuItem onSelect={() => logout().then(() => toast.success('Logged out'))}>
                  <LogOut /> Log out
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarFooter>
      <SidebarRail />
    </Sidebar>
  );
}
