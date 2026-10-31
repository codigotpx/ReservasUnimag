/** Fechas y horas siempre en la zona de la universidad (UC1 FR-010), sin importar la del navegador. */
export const ZONE = "America/Bogota";

const isoDay = new Intl.DateTimeFormat("en-CA", {
  timeZone: ZONE,
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
});
const hourMinute = new Intl.DateTimeFormat("en-GB", {
  timeZone: ZONE,
  hour: "2-digit",
  minute: "2-digit",
  hourCycle: "h23",
});
const weekday = new Intl.DateTimeFormat("es-CO", { timeZone: ZONE, weekday: "short" });
const dayAndMonth = new Intl.DateTimeFormat("es-CO", { timeZone: ZONE, day: "numeric", month: "long" });

/** yyyy-MM-dd de hoy en Bogotá. */
export function today(now = new Date()): string {
  return isoDay.format(now);
}

export function addDays(date: string, days: number): string {
  const day = new Date(`${date}T12:00:00Z`);
  day.setUTCDate(day.getUTCDate() + days);
  return day.toISOString().slice(0, 10);
}

/** "Hoy", "Mañana" o null. */
export function relativeDay(date: string, now = new Date()): string | null {
  const current = today(now);
  if (date === current) return "Hoy";
  if (date === addDays(current, 1)) return "Mañana";
  return null;
}

/** "24 de agosto". */
export function dayMonth(date: string): string {
  return dayAndMonth.format(midday(date));
}

/** "Mié, 24 de agosto". */
export function weekdayDayMonth(date: string): string {
  const name = weekday.format(midday(date)).replace(".", "");
  return `${name.charAt(0).toUpperCase()}${name.slice(1)}, ${dayMonth(date)}`;
}

/** "Hoy, miércoles 24 de agosto", o solo "Jue, 26 de agosto" si no es hoy ni mañana. */
export function longDayLabel(date: string): string {
  const relative = relativeDay(date);
  return relative ? `${relative}, ${longWeekday.format(midday(date))} ${dayMonth(date)}` : weekdayDayMonth(date);
}

/** "Hoy", "Mañana" o "26 de agosto". */
export function shortDayLabel(date: string): string {
  return relativeDay(date) ?? dayMonth(date);
}

/** "hoy", "mañana" o "el 26 de agosto", para usar dentro de una frase. */
export function dayInSentence(date: string): string {
  const relative = relativeDay(date);
  return relative ? relative.toLowerCase() : `el ${dayMonth(date)}`;
}

/** La hora "HH:mm" de un instante ISO, en Bogotá. */
export function timeOf(instant: string): string {
  return hourMinute.format(new Date(instant));
}

/** El día yyyy-MM-dd de un instante ISO, en Bogotá. */
export function dayOf(instant: string): string {
  return isoDay.format(new Date(instant));
}

/**
 * La franja que se propone al abrir la búsqueda: dos horas desde la próxima hora en punto,
 * dentro de la ventana de 06:00 a 22:00. Ya tarde, la propone para mañana a las 08:00.
 */
export function defaultSlot(now = new Date()): { date: string; start: string; end: string } {
  const nextHour = Number(timeOf(now.toISOString()).slice(0, 2)) + 1;
  if (nextHour > 20) {
    return { date: addDays(today(now), 1), start: "08:00", end: "10:00" };
  }
  const start = Math.max(nextHour, 6);
  return { date: today(now), start: `${pad(start)}:00`, end: `${pad(start + 2)}:00` };
}

function pad(hour: number): string {
  return String(hour).padStart(2, "0");
}

function midday(date: string): Date {
  return new Date(`${date}T12:00:00-05:00`);
}

const longWeekday = new Intl.DateTimeFormat("es-CO", { timeZone: ZONE, weekday: "long" });

/** "Miércoles, 24 de agosto". */
export function fullDayLabel(date: string): string {
  const name = longWeekday.format(midday(date));
  return `${name.charAt(0).toUpperCase()}${name.slice(1)}, ${dayMonth(date)}`;
}

/** Minutos entre dos horas "HH:mm" del mismo día. */
export function minutesBetween(start: string, end: string): number {
  return toMinutes(end) - toMinutes(start);
}

/** "2 h 0 min", "45 min". */
export function durationLabel(minutes: number): string {
  const hours = Math.floor(minutes / 60);
  return hours > 0 ? `${hours} h ${minutes % 60} min` : `${minutes} min`;
}

/** Suma minutos a una hora "HH:mm", sin pasar de las 22:00. */
export function addMinutes(time: string, minutes: number): string {
  const total = Math.min(toMinutes(time) + minutes, 22 * 60);
  return `${String(Math.floor(total / 60)).padStart(2, "0")}:${String(total % 60).padStart(2, "0")}`;
}

function toMinutes(time: string): number {
  const [hours, minutes] = time.split(":").map(Number);
  return hours * 60 + minutes;
}

/** Las horas de 06:00 a 22:00 de media en media hora, en formato de 24 horas. */
export const HALF_HOURS = Array.from({ length: 33 }, (_, index) => {
  const minutes = 6 * 60 + index * 30;
  return `${String(Math.floor(minutes / 60)).padStart(2, "0")}:${minutes % 60 === 0 ? "00" : "30"}`;
});

const shortDayMonth = new Intl.DateTimeFormat("es-CO", { timeZone: ZONE, day: "numeric", month: "short" });
const monthName = new Intl.DateTimeFormat("es-CO", { timeZone: ZONE, month: "long" });
const yearNumber = new Intl.DateTimeFormat("es-CO", { timeZone: ZONE, year: "numeric" });

/** "15 sept". */
export function shortDay(date: string): string {
  return shortDayMonth.format(midday(date)).replace(".", "");
}

/** "Septiembre 2026", para agrupar por mes. */
export function monthYear(date: string): string {
  const name = monthName.format(midday(date));
  return `${name.charAt(0).toUpperCase()}${name.slice(1)} ${yearNumber.format(midday(date))}`;
}

/** "2 h 15 min", "25 min" o "3 días": cuánto falta, redondeado hacia abajo. */
export function timeUntil(minutes: number): string {
  if (minutes >= 24 * 60) {
    const days = Math.floor(minutes / (24 * 60));
    return `${days} ${days === 1 ? "día" : "días"}`;
  }
  return durationLabel(Math.max(0, minutes)).replace(/ 0 min$/, "");
}

const clock = new Intl.DateTimeFormat("es-CO", { timeZone: ZONE, hour: "numeric", minute: "2-digit", hour12: true });

/** "4:10 p. m.", la hora como la lee una persona. */
export function clockTime(instant: string): string {
  return clock.format(new Date(instant)).replace(/\s/g, " ");
}
