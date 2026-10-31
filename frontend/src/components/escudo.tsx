import Image from "next/image";

/** El escudo de la universidad con su nombre en arco (escudo.webp lo trae en blanco). */
export function Escudo({ className, preload }: { className?: string; preload?: boolean }) {
  return (
    <Image
      src="/images/escudo.webp"
      alt="Escudo de la Universidad del Magdalena"
      width={157}
      height={152}
      preload={preload}
      className={`h-auto ${className ?? ""}`}
    />
  );
}
