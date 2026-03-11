# SoundNest: AI-Powered Music Player

SoundNest is a modern, feature-rich music player built with JavaFX and enhanced by Artificial Intelligence. It features an AI Auto-Equalizer that intelligently predicts the best EQ settings for any song using a Python-based audio analysis engine.

---

## Key Features

- **AI Auto-Equalizer**: Automatically adjusts 5-band EQ settings based on song characteristics (Bass, Mid, Treble) using a machine learning model.
- **Cloud-Powered Persistence**: Fully integrated with Aiven MySQL for live, cloud-based song storage and synchronization.
- **Local Migration**: Seamlessly migrates your existing local XAMPP database to the Aiven cloud on first startup.
- **Smart Library**: Browse and manage your local music folders with an intuitive TreeView interface.
- **Queue & Playlists**: Add songs to your next-up queue or manage a central playlist with drag-and-drop ease.
- **Dynamic UI**: Features a sleek, dark-themed interface with smooth RGB-animated headers.

---

## Tech Stack

- **Frontend**: JavaFX (UI/UX)
- **Backend Logic**: Java 24
- **Database**: MySQL (Hosted on Aiven Cloud)
- **AI/ML Engine**: Python 3.12 (librosa, scikit-learn)
- **Communication**: JDBC (Java-Database), Subprocess (Java-Python)

---

## Setup & Installation

### Prerequisites
- **JDK 24** (Required for JavaFX 24 compatibility)
- **Python 3.12** (with `librosa`, `numpy`, and `joblib` installed)
- **MySQL Connector/J** library in your classpath.

### Running the App
1. Clone the repository:
   ```bash
   git clone https://github.com/EzazAzhar/MusicPlayer-With-AI-AutoEqualizer.git
   ```
2. Update the `YOUR_AIVEN_PASSWORD_HERE` placeholder in `MusicPlayerApp.java` with your actual Aiven password.
3. Compile and Run using JDK 24:
   ```powershell
   & 'pth/to/your/jdk-24/bin/java.exe' ... MusicPlayerApp
   ```

---

## Security Note
The project uses placeholder credentials for the cloud database on GitHub to bypass secret scanning. Ensure you update your local `MusicPlayerApp.java` with the correct credentials provided in your Aiven dashboard.

---

## License
This project is for educational purposes. Feel free to fork and enhance!

---

**Developed with by [EzazAzhar](https://github.com/EzazAzhar)**