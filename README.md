<div align="center">

```
  ██████╗ ██╗   ██╗     ██╗   ██╗██╗███████╗██╗ ██████╗ ███╗   ██╗
  ██╔══██╗██║   ██║     ██║   ██║██║██╔════╝██║██╔═══██╗████╗  ██║
  ██████╔╝██║   ██║     ██║   ██║██║███████╗██║██║   ██║██╔██╗ ██║
  ██╔══██╗╚██╗ ██╔╝     ╚██╗ ██╔╝██║╚════██║██║██║   ██║██║╚██╗██║
  ██║  ██║ ╚████╔╝       ╚████╔╝ ██║███████║██║╚██████╔╝██║ ╚████║
  ╚═╝  ╚═╝  ╚═══╝         ╚═══╝  ╚═╝╚══════╝╚═╝ ╚═════╝ ╚═╝  ╚═══╝
```

### ⚡ NEXT-GEN CYBERPUNK VIRTUAL REALITY ENGINE FOR MINECRAFT 1.21.1 ⚡
#### *Native Stereo VR Engine // Fabric Loader // Sodium Plus High-FPS Optimization*

---

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-32CD32?style=for-the-badge&logo=minecraft&logoColor=white)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Fabric-Loader%200.16+-black?style=for-the-badge&logo=fabric&logoColor=white)](https://fabricmc.net/)
[![Sodium Plus](https://img.shields.io/badge/Sodium%20Plus-Optimized%2090%20FPS-00F5FF?style=for-the-badge&logo=nvidia&logoColor=black)](https://modrinth.com/)
[![OpenVR](https://img.shields.io/badge/SteamVR-OpenVR%20%2F%20OpenXR-7928CA?style=for-the-badge&logo=steam&logoColor=white)](https://store.steampowered.com/app/250820/SteamVR/)
[![Java](https://img.shields.io/badge/Java-21%20%2F%2025-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://adoptium.net/)

---

</div>

<br/>

```
▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓
░░░░░░░░░░░░░░░░░░░░░ [ SYSTEM CORE // INITIALIZED ] ░░░░░░░░░░░░░░░░░░░░░░░░░░░░
▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓
```

## 🌌 Visión General (Overview)

**RV-Visor** es una arquitectura de realidad virtual avanzada y de alto rendimiento diseñada específicamente para **Minecraft 1.21.1 (Fabric)**. Transforma la experiencia cúbica tradicional en una inmersión holográfica de grado cibernético con seguimiento 6-DoF nativo, menús anclados espacialmente en 3D, puntero láser interactivo y sincronización perfecta con **Sodium Plus** para alcanzar **90 - 120 FPS estables**.

---

## ⚡ Características Principales (Key Features)

### 🥽 1. Menús Flotantes con Anclaje Espacial 3D (Spatial-Anchored Holo-UI)
* **Inmóviles en el Aire:** Al abrir el Inventario (**`X`**) o el Menú de Pausa (**`Y` / Escape**), el panel holográfico se clava en las coordenadas 3D del espacio de seguimiento. Puedes girar la cabeza o caminar en roomscale y el menú permanece 100% fijo en su lugar físico.
* **Transparencia Cristalina:** Cero fondos negros ni recuadros oscuros. El mundo 3D de Minecraft permanece visible, iluminado y vivo detrás de las ventanas.
* **Puntero Láser Neón:** Raycast interactivo 1:1 desde el mando derecho con punto de mira focal.
  * **Gatillo (Trigger):** Clic izquierdo (Coger stack / interactuar).
  * **Grip (Agarre):** Clic derecho (Dividir stack / colocar 1 ítem).

---

### 🛡️ 2. HUD Estilo Pegatina (Bottom-Right Sticker HUD)
* **Discreto y No Invasivo:** Ubicado como un widget holográfico limpio en la esquina inferior derecha del visor.
* **Información Vital al Instante:** Muestra Corazones de Salud ❤️, Muslos de Comida 🍗, Nivel de Experiencia, Puntos de Armadura y los 9 Slots del Hotbar con iconos nítidos y transparentes.
* **Campo de Visión 100% Despejado:** Tu visión central y periférica queda totalmente libre para explorar, combatir y construir.

---

### 🌊 3. Integración Total con Sodium Plus (Zero-Clipping Engine)
* **Arquitectura `WindowMixin`:** Sincronización a nivel de bytecode para que el motor de optimización de chunks y fluidos de Sodium trabaje a la resolución ultra-alta nativa del ojo.
* **Agua y Cielo al 100%:** Sin cortes verticales, sin líneas de tijera (`Scissor`) y sin artefactos visuales en el horizonte.
* **Supersampling Puro:** Máxima densidad de píxeles por grado (PPD) sin ningún zoom óptico ni estiramiento de pantalla.

---

### ⚙️ 4. Menú de Configuración Óptica & Persistencia en Disco
* **Guardado Automático:** Archivo de configuración en `config/rvvisor.json` para recordar tus ajustes de escala de renderizado, anti-aliasing y nitidez entre sesiones.
* **Anti-Aliasing Hardware (MSAA 2x / 4x / 8x):** Elimina los dientes de sierra en las hojas y bordes de los bloques.
* **Filtro AMD FidelityFX CAS:** Nitidez de contraste opcional ajustable desde `OFF` (Recomendado NVIDIA) hasta `100%`.

---

```
▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓
░░░░░░░░░░░░░░░░░░░░ [ CONTROLES // VR MAPPINGS ] ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░
▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓
```

## 🎮 Mapa de Controles (VR Controller Layout)

| Botón / Control | Acción en Juego | Acción en Menú / Inventario |
| :--- | :--- | :--- |
| **Joystick Izquierdo** | Moverse / Caminar (Impulso Analógico) | *Bloqueado (Personaje Fijo)* |
| **Joystick Derecho** | Giro de Cámara Suave (Smooth Turn) | *Bloqueado (Personaje Fijo)* |
| **Gatillo Derecho (Right Trigger)** | Golpear / Romper Bloque / Atacar | Clic Izquierdo (Seleccionar / Mover Stack) |
| **Grip Derecho (Right Grip)** | Usar Ítem / Colocar Bloque | Clic Derecho (Dividir Stack / Colocar 1) |
| **Botón X (Mando Izquierdo)** | Abrir Inventario | **Cerrar Inventario al Instante** |
| **Botón Y (Mando Izquierdo)** | Menú de Pausa / Escape | **Cerrar Menú de Pausa** |
| **Botón A (Mando Derecho)** | Saltar (Jump) | - |
| **Botón B (Mando Derecho)** | Agacharse (Sneak) | - |
| **Left Grip + Stick Derecho** | Ciclar Rápido entre Ítems del Hotbar | - |

---

```
▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓
░░░░░░░░░░░░░░░░░░░░ [ INSTALACIÓN // QUICK START ] ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░
▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓
```

## 🚀 Instalación y Compilación

### Requisitos:
* **Java JDK 21** (o superior, ej. JDK 25)
* **Minecraft 1.21.1** con **Fabric Loader 0.16+**
* **Sodium** / **Sodium Plus** instalado en tu perfil
* **SteamVR** ejecutándose en tu PC con tus gafas VR conectadas (Meta Quest 2/3/Pro vía Link/AirLink/Virtual Desktop, Valve Index, HTC Vive, Pico 4 o WMR).

### Compilación desde el Código Fuente:
```bash
# Clona el repositorio
git clone https://github.com/tu-usuario/RV-Visor-Minecraft.git
cd RV-Visor-Minecraft

# Compila el archivo .jar optimizado
./gradlew build -x test --no-daemon
```

El archivo compilado se generará en `build/libs/rvvisor-1.0.0.jar`. Cópialo a tu carpeta `.minecraft/mods/` o en tu perfil de **Modrinth (Sodium Plus)** y ¡listo para jugar!

---

<div align="center">

```
  ██████╗ ██╗   ██╗     ██╗   ██╗██╗███████╗██╗ ██████╗ ███╗   ██╗
  ██║     ╚██╗ ██╔╝     ██║   ██║██║██╔════╝██║██╔═══██╗████╗  ██║
  ██║      ╚████╔╝      ██║   ██║██║███████╗██║██║   ██║██╔██╗ ██║
  ██║       ╚██╔╝       ╚██╗ ██╔╝██║╚════██║██║██║   ██║██║╚██╗██║
  ███████╗   ██║         ╚████╔╝ ██║███████║██║╚██████╔╝██║ ╚████║
  ╚══════╝   ╚═╝          ╚═══╝  ╚═╝╚══════╝╚═╝ ╚═════╝ ╚═╝  ╚═══╝
```

**⚡ RV-VISOR // NEURAL CYBERPUNK VR INTERFACE ⚡**  
*Desarrollado con pasión para la comunidad de Minecraft VR.*

</div>
