import Image from "next/image";

/**
 * Las dos versiones oficiales del escudo con su nombre en arco (cdn.unimagdalena.edu.co/images/escudo): sobre fondo
 * claro el nombre va oscuro; sobre fondo oscuro, como la foto del login, va en blanco.
 */
const SOURCES = {
  light: { src: "/images/escudo-claro.webp", width: 148, height: 144 },
  dark: { src: "/images/escudo.webp", width: 157, height: 152 },
} as const;

export function Escudo({
  className,
  preload,
  surface = "light",
}: {
  className?: string;
  preload?: boolean;
  /** El fondo sobre el que va el escudo. */
  surface?: "light" | "dark";
}) {
  const { src, width, height } = SOURCES[surface];
  return (
    <Image
      src={src}
      alt="Escudo de la Universidad del Magdalena"
      width={width}
      height={height}
      preload={preload}
      className={`h-auto ${className ?? ""}`}
    />
  );
}
