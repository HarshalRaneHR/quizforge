import type { ReactNode } from 'react';
import {
  Bar, BarChart, CartesianGrid, LabelList, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';

/*
 * Chart conventions (single series everywhere, so one validated hue and no legend; the title names it):
 * - series colour: --chart-1 (checked for lightness band + >=3:1 contrast on light and dark cards)
 * - text (ticks, labels, tooltips) uses text tokens, never the series colour
 * - 2px lines with >=8px markers; bars capped at 24px with a 4px rounded data end
 * - hairline, solid, recessive gridlines; hover tooltip on every chart
 */
const SERIES = 'var(--chart-1)';
const AXIS = { fill: 'var(--muted-foreground)', fontSize: 12 };
const GRID = 'var(--border)';

function TooltipBox({ title, children }: { title: ReactNode; children: ReactNode }) {
  return (
    <div className="rounded-lg border bg-popover px-3 py-2 text-sm text-popover-foreground shadow-md">
      <p className="font-medium">{title}</p>
      <div className="text-muted-foreground">{children}</div>
    </div>
  );
}

export type TrendDatum = { label: string; value: number; detail?: string };

/** Score over successive attempts (0–100%). */
export function TrendChart({ data, height = 240, ariaLabel }: { data: TrendDatum[]; height?: number; ariaLabel: string }) {
  return (
    <div role="img" aria-label={ariaLabel} style={{ height }}>
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={data} margin={{ top: 12, right: 16, bottom: 4, left: -12 }}>
          <CartesianGrid stroke={GRID} strokeWidth={1} vertical={false} />
          <XAxis dataKey="label" tick={AXIS} tickLine={false} axisLine={{ stroke: GRID }} interval="preserveStartEnd" minTickGap={24} />
          <YAxis domain={[0, 100]} ticks={[0, 25, 50, 75, 100]} tickFormatter={v => `${v}%`} tick={AXIS} tickLine={false} axisLine={false} />
          <Tooltip
            cursor={{ stroke: 'var(--muted-foreground)', strokeWidth: 1 }}
            content={({ active, payload }) => {
              if (!active || !payload?.length) return null;
              const d = payload[0].payload as TrendDatum;
              return <TooltipBox title={`${d.value}%`}>{d.detail ?? d.label}</TooltipBox>;
            }}
          />
          <Line type="monotone" dataKey="value" stroke={SERIES} strokeWidth={2} strokeLinecap="round" strokeLinejoin="round"
                dot={{ r: 4, fill: SERIES, stroke: 'var(--card)', strokeWidth: 2 }}
                activeDot={{ r: 6, fill: SERIES, stroke: 'var(--card)', strokeWidth: 2 }} isAnimationActive={false} />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}

export type BarDatum = { label: string; value: number; detail?: string };

/** Horizontal bars for a few labelled values (e.g. accuracy per question type), value at the bar tip. */
export function HorizontalBars({ data, max = 100, suffix = '%', ariaLabel }: {
  data: BarDatum[]; max?: number; suffix?: string; ariaLabel: string;
}) {
  const height = Math.max(120, data.length * 40 + 24);
  return (
    <div role="img" aria-label={ariaLabel} style={{ height }}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} layout="vertical" margin={{ top: 4, right: 48, bottom: 4, left: 8 }} barCategoryGap={12}>
          <CartesianGrid stroke={GRID} strokeWidth={1} horizontal={false} />
          <XAxis type="number" domain={[0, max]} hide />
          <YAxis type="category" dataKey="label" width={120} tick={AXIS} tickLine={false} axisLine={false} />
          <Tooltip
            cursor={{ fill: 'var(--muted)', opacity: 0.5 }}
            content={({ active, payload }) => {
              if (!active || !payload?.length) return null;
              const d = payload[0].payload as BarDatum;
              return <TooltipBox title={d.label}>{d.value}{suffix}{d.detail ? ` · ${d.detail}` : ''}</TooltipBox>;
            }}
          />
          <Bar dataKey="value" fill={SERIES} maxBarSize={24} radius={[0, 4, 4, 0]} isAnimationActive={false}>
            <LabelList dataKey="value" position="right" formatter={(v: unknown) => `${v}${suffix}`}
                       style={{ fill: 'var(--foreground)', fontSize: 12, fontWeight: 600 }} />
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

/** Vertical columns over ordered categories (score buckets, days). */
export function Columns({ data, height = 220, ariaLabel, valueName }: {
  data: BarDatum[]; height?: number; ariaLabel: string; valueName: string;
}) {
  return (
    <div role="img" aria-label={ariaLabel} style={{ height }}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ top: 12, right: 8, bottom: 4, left: -20 }} barCategoryGap="20%">
          <CartesianGrid stroke={GRID} strokeWidth={1} vertical={false} />
          <XAxis dataKey="label" tick={AXIS} tickLine={false} axisLine={{ stroke: GRID }} interval="preserveStartEnd" />
          <YAxis allowDecimals={false} tick={AXIS} tickLine={false} axisLine={false} />
          <Tooltip
            cursor={{ fill: 'var(--muted)', opacity: 0.5 }}
            content={({ active, payload }) => {
              if (!active || !payload?.length) return null;
              const d = payload[0].payload as BarDatum;
              return <TooltipBox title={d.detail ?? d.label}>{d.value} {valueName}</TooltipBox>;
            }}
          />
          <Bar dataKey="value" fill={SERIES} maxBarSize={24} radius={[4, 4, 0, 0]} isAnimationActive={false} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
