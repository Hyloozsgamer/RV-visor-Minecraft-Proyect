<div align="center">

# 🥽 RV-VISOR // MINECRAFT VR MOD
### *Motor de Realidad Virtual Nativo para Minecraft 1.21.1 Fabric + Sodium Plus*

<br/>

<img src="assets/header.png" alt="RV-Visor Minecraft VR Banner" width="800" style="border-radius: 8px; box-shadow: 0 4px 20px rgba(0,0,0,0.3);" />

<br/><br/>

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-32CD32?style=for-the-badge&logo=minecraft&logoColor=white)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Fabric-Loader%200.16+-black?style=for-the-badge&logo=fabric&logoColor=white)](https://fabricmc.net/)
[![Sodium Plus](https://img.shields.io/badge/Sodium%20Plus-Optimized%2090%20FPS-00BFFF?style=for-the-badge&logo=nvidia&logoColor=black)](https://modrinth.com/)
[![OpenVR](https://img.shields.io/badge/SteamVR-OpenVR%20%2F%20OpenXR-7928CA?style=for-the-badge&logo=steam&logoColor=white)](https://store.steampowered.com/app/250820/SteamVR/)
[![Java](https://img.shields.io/badge/Java-21%20%2F%2025-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://adoptium.net/)

---

<br/>

<img src="assets/title_overview.png" alt="Visión General" width="850" />

</div>

<br/>

**RV-Visor** es un mod de realidad virtual de alto rendimiento diseñado exclusivamente para **Minecraft 1.21.1 (Fabric)**. Permite jugar en VR con seguimiento posicional 6-DoF nativo, menús anclados espacialmente en 3D con puntero láser interactivo y sincronización completa con **Sodium Plus** para mantener **90 - 120 FPS estables**.

---

<div align="center">

<br/>

<img src="assets/title_features.png" alt="Características" width="850" />

<br/>

</div>

### 🥽 1. Menús Flotantes con Anclaje Espacial 3D

<div align="center">

<img src="assets/spatial_menu.png" alt="Menú Espacial 3D con Puntero Láser" width="650" style="border-radius: 8px; margin: 15px 0;" />

</div>

* **Fijación Total en el Espacio:** Al abrir el Inventario (**`X`**) o el Menú de Pausa (**`Y` / Escape**), el panel queda flotando en las coordenadas 3D del mundo. Puedes mover la cabeza o desplazarte libremente y el menú permanecerá 100% inmóvil en su sitio físico.
* **Transparencia Cristalina:** Sin fondos negros ni cajas oscuras; el entorno de Minecraft permanece iluminado y visible detrás.
* **Puntero Láser Preciso:** Raycast interactivo desde el mando derecho con punto focal:
  * **Gatillo Derecho:** Clic izquierdo (Mover/coger stack).
  * **Grip Derecho:** Clic derecho (Dividir stack o colocar 1 ítem).

---

### 🛡️ 2. HUD Estilo Pegatina (Bottom-Right Sticker HUD)
* **Discreto y Fuera de la Vista Central:** Ubicado limpiamente en la esquina inferior derecha del visor.
* **Lectura Instantánea:** Corazones de vida, comida, nivel de XP, armadura y la barra de 9 slots con iconos nítidos y transparentes.
* **Visión Despejada:** Máxima inmersión sin paneles molestos en medio del campo de visión.

---

### 🌊 3. Integración Total con Sodium Plus
* **Sin Recortes de Imagen (`WindowMixin`):** Sincronización a bajo nivel para que Sodium renderice el agua, niebla y cielo al 100% del campo de visión sin cortes verticales.
* **Supersampling Real:** Densidad de píxeles nativa sin deformaciones ópticas ni zoom.
* **Anti-Aliasing Hardware (MSAA 2x/4x) y Nitidez:** Bordes limpios sin dientes de sierra en bloques ni vegetación.

---

### 💾 4. Configuración Persistente en Disco
* **Guardado Automático:** Archivo `config/rvvisor.json` para guardar tus ajustes de resolución, MSAA y nitidez entre reinicios del juego.

---

<div align="center">

<br/>

<img src="assets/title_controls.png" alt="Controles VR" width="850" />

<br/><br/>

<img src="assets/controllers.png" alt="Esquema de Mandos VR" width="650" style="border-radius: 8px; margin: 15px 0;" />

</div>

<br/>

| Botón / Control | Acción en Juego | Acción en Menú / Inventario |
| :--- | :--- | :--- |
| **Joystick Izquierdo** | Moverse / Caminar | *Bloqueado (Personaje Fijo)* |
| **Joystick Derecho** | Giro de Cámara Suave (Smooth Turn) | *Bloqueado (Personaje Fijo)* |
| **Gatillo Derecho (Right Trigger)** | Golpear / Romper Bloque / Atacar | Clic Izquierdo (Seleccionar / Mover Stack) |
| **Grip Derecho (Right Grip)** | Usar Ítem / Colocar Bloque | Clic Derecho (Dividir Stack / Colocar 1) |
| **Botón X (Mando Izquierdo)** | Abrir Inventario | **Cerrar Inventario al Instante** |
| **Botón Y (Mando Izquierdo)** | Menú de Pausa / Escape | **Cerrar Menú de Pausa** |
| **Botón A (Mando Derecho)** | Saltar (Jump) | - |
| **Botón B (Mando Derecho)** | Agacharse (Sneak) | - |
| **Left Grip + Stick Derecho** | Ciclar Rápido entre Ítems del Hotbar | - |

---

<div align="center">

<br/>

<img src="assets/title_install.png" alt="Instalación" width="850" />

<br/>

</div>

### Requisitos:
* **Java JDK 21** (o superior)
* **Minecraft 1.21.1** con **Fabric Loader 0.16+**
* **Sodium** / **Sodium Plus**
* **SteamVR** con tu visor conectado (Meta Quest 2/3/Pro, Valve Index, HTC Vive, Pico 4, WMR).

### Compilación:
```bash
git clone git@github.com:Hyloozsgamer/RV-visor-Minecraft-Proyect.git
cd RV-visor-Minecraft-Proyect
./gradlew build -x test --no-daemon
```

El archivo compilado se genera en `build/libs/rvvisor-1.0.0.jar`. Cópialo a tu carpeta de mods o en tu perfil de Modrinth.

---

<div align="center">

**RV-VISOR // VIRTUAL REALITY FOR MINECRAFT**  
*Desarrollado para la comunidad de Minecraft VR.*

</div>
