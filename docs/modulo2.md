# 🗓️ Módulo 2: Operación de Reservas y Priorización Académica
Es el motor de reglas de negocio encargado de gestionar el uso y resolver conflictos de interés.

#### 2.1. Módulo de Carga Académica (Prioridad Institucional)
Permite a la administración asegurar la continuidad de las actividades principales de la universidad:
*   **Importar Horarios Semestrales:** Carga de clases fijas que marcan automáticamente los espacios como "Bloqueo Académico".
*   **Jerarquía de Reserva:** Las reservas académicas tienen prioridad total sobre las estudiantiles, permitiendo cancelaciones automáticas de menor rango si surge una necesidad institucional extraordinaria.

#### 2.2. Interfaz de Apartado Estudiantil
*   **Validación de Disponibilidad:** Los estudiantes solo pueden visualizar recursos que no estén en "Bloqueo Académico" o "En Uso".
*   **Diccionario de Errores (Denegación):** Se impide la reserva por conflictos académicos, haber alcanzado el límite máximo de préstamos o tener una sanción activa.