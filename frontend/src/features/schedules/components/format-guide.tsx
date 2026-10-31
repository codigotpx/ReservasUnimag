import { Check, Download } from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";

const TEMPLATE_URL = "/plantillas/horarios.csv";

/** Las nueve columnas que exige CsvScheduleReader (RawScheduleRow.REQUIRED_COLUMNS más instructor). */
const COLUMNS: [name: string, required: boolean, what: string, example: string][] = [
  ["course_code", true, "Código de la asignatura.", "IS-402"],
  ["course", true, "Nombre de la asignatura.", "Redes de Computadores"],
  ["group", true, "Grupo. Dos grupos de la misma asignatura son clases distintas.", "1"],
  ["program", true, "Programa académico.", "Ingeniería de Sistemas"],
  ["instructor", false, "Docente. Puede ir vacío si todavía no se asignó.", "Nombre del docente"],
  ["resource_id", true, "Código del espacio o activo en el inventario, tal como aparece en Consultar recursos.", "ESP-0412"],
  ["day_of_week", true, "LUNES, MARTES, MIERCOLES, JUEVES, VIERNES o SABADO. En mayúsculas y sin tildes; los domingos no hay clases.", "LUNES"],
  ["start_time", true, "Hora de inicio en formato HH:mm de 24 horas, desde las 06:00.", "08:00"],
  ["end_time", true, "Hora de fin en formato HH:mm, después de la de inicio y hasta las 22:00.", "10:00"],
];

const SAMPLE = [
  "course_code;course;group;program;instructor;resource_id;day_of_week;start_time;end_time",
  "IS-402;Redes de Computadores;1;Ingeniería de Sistemas;Nombre del docente;ESP-0412;LUNES;08:00;10:00",
  "IS-510;Seminario de Investigación;1;Ingeniería de Sistemas;;ESP-0301;JUEVES;14:00;16:00",
  "IC-110;Dibujo Técnico, taller;1;Ingeniería Civil;Nombre del docente;ESP-0150;VIERNES;14:00;16:00",
];

const MISTAKES: [problem: string, fix: string][] = [
  [
    "Al abrirlo, todo queda en una sola columna",
    "El archivo usa un separador distinto al que espera tu Excel. Descarga de nuevo la plantilla, que viene con punto y coma, o ábrelo desde la pestaña Datos, con «Desde texto/CSV», y elige el separador. El sistema acepta los dos.",
  ],
  [
    "«Falta la columna …» y la carga no empieza",
    "La cabecera debe tener los nueve nombres exactos, en minúsculas y en inglés. Descarga la plantilla y copia tus datos en ella.",
  ],
  ["Hora mal escrita", "Siempre HH:mm de 24 horas: 08:00, 14:30. No sirven 8:00 AM, 8.00 ni 9:3."],
  ["Día no válido", "MIERCOLES y SABADO van sin tilde y todo en mayúsculas. «Miércoles» se rechaza."],
  ["El recurso no existe", "Usa el código exacto del inventario (por ejemplo ESP-0412), no el nombre del salón."],
  ["Choca con otra clase", "Dos clases en el mismo recurso y a la misma hora. Corrige una de las dos filas."],
];

/** Cómo debe ser el CSV del horario (diseño admin-formato-archivo). Sale del código que lo lee, no solo del plan. */
export function FormatGuide() {
  return (
    <div className="mx-auto flex w-full max-w-6xl flex-col gap-6 px-4 py-6 sm:px-8 lg:px-10 lg:py-10">
      <p className="text-sm text-gray-500">
        Así debe estar armado el CSV para que el sistema lo lea. Cada fila describe una clase que se repite todas las
        semanas; el sistema la convierte en sesiones entre las fechas del periodo que eliges al subirlo.
      </p>

      <Card title="Reglas generales">
        <ul className="flex flex-col gap-3">
          {[
            "Formato .csv de máximo 2 MB. Sirve tal como lo guarda Excel: «CSV UTF-8» o «CSV (delimitado por comas)».",
            "Separado por punto y coma, como la plantilla, o por comas: el sistema reconoce los dos. Si un valor tiene el separador adentro, va entre comillas dobles.",
            "La primera fila es la cabecera, con los nombres de columna exactamente como en la tabla de abajo, en minúsculas.",
            "Una fila es una clase semanal, no una sesión: \"Redes, lunes de 08:00 a 10:00\" se repite cada lunes del periodo.",
            "El semestre y las fechas de inicio y fin no van en el archivo: se eligen en la pantalla al subirlo.",
            "Las columnas de más se ignoran y las filas en blanco se saltan.",
          ].map((rule) => (
            <li key={rule} className="flex gap-3 text-sm text-foreground">
              <Check className="mt-0.5 size-4 shrink-0 text-green-600" strokeWidth={3} />
              {rule}
            </li>
          ))}
        </ul>
      </Card>

      <Card title="Columnas">
        <div className="overflow-x-auto rounded-2xl border border-gray-200">
          <table className="w-full min-w-[44rem] text-left text-sm">
            <thead className="bg-gray-50 text-xs font-semibold uppercase tracking-wide text-gray-500">
              <tr>
                <th className="px-4 py-3">Columna</th>
                <th className="px-4 py-3">Obligatoria</th>
                <th className="px-4 py-3">Qué va</th>
                <th className="px-4 py-3">Ejemplo</th>
              </tr>
            </thead>
            <tbody>
              {COLUMNS.map(([name, required, what, example]) => (
                <tr key={name} className="border-t border-gray-100 align-top">
                  <td className="px-4 py-3">
                    <code className="rounded-md bg-brand-navy/5 px-1.5 py-0.5 font-mono text-brand-navy">{name}</code>
                  </td>
                  <td className="px-4 py-3">{required ? "Sí" : "No"}</td>
                  <td className="px-4 py-3">{what}</td>
                  <td className="whitespace-nowrap px-4 py-3 font-mono">{example}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      <Card title="Ejemplo">
        <p className="text-sm text-gray-500">
          Las primeras filas de un archivo válido, separado por punto y coma como la plantilla. La segunda clase no tiene
          docente todavía: esa columna va vacía.
        </p>
        <pre className="overflow-x-auto rounded-2xl bg-brand-night p-5 font-mono text-sm leading-6 text-[#e8edf2]">
          {SAMPLE.map((line, index) => (
            <span key={line} className={index === 0 ? "block text-[#7fb8ff]" : "block"}>
              {line}
            </span>
          ))}
        </pre>
      </Card>

      <Card title="Errores frecuentes y cómo corregirlos">
        <dl className="divide-y divide-gray-100 border-t border-gray-100">
          {MISTAKES.map(([problem, fix]) => (
            <div key={problem} className="grid gap-1 py-3.5 sm:grid-cols-[18rem_1fr] sm:gap-6">
              <dt className="text-sm font-semibold text-foreground">{problem}</dt>
              <dd className="text-sm text-gray-600">{fix}</dd>
            </div>
          ))}
        </dl>
        <p className="text-sm text-gray-500">
          Una sola fila con error impide aplicar la carga: el reporte de validación dice la línea del archivo y la
          columna, para corregirlas todas de una vez.
        </p>
      </Card>

      <div className="flex flex-wrap justify-end gap-3">
        <a
          href={TEMPLATE_URL}
          download="plantilla-horarios.csv"
          className="inline-flex h-12 items-center justify-center gap-2 rounded-full border-2 border-brand-navy bg-white px-6 font-semibold text-brand-navy transition hover:bg-brand-navy/5"
        >
          <Download className="size-4" /> Descargar plantilla vacía
        </a>
        <Link
          href="/schedules"
          className="inline-flex h-12 items-center justify-center gap-2 rounded-full bg-brand-navy px-7 font-semibold text-white transition hover:bg-brand-navy/90"
        >
          Volver a importar
        </Link>
      </div>
    </div>
  );
}

function Card({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-4 rounded-3xl border border-gray-200 bg-white p-5 sm:p-7">
      <h2 className="text-lg font-bold text-foreground">{title}</h2>
      {children}
    </section>
  );
}
