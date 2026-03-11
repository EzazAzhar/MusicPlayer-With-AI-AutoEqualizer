import joblib
import librosa
import numpy as np
import sys
import os

# Load the model from the same folder as this script
model_path = os.path.join(os.path.dirname(__file__), "eq_model.pkl")
model = joblib.load(model_path)

# Get song path from argument or default for testing
if len(sys.argv) > 1:
    song_path = sys.argv[1]
else:
    song_path = r"D:\Musics\Queen-Bohemian.mp3"

# Extract features
y, sr = librosa.load(song_path, duration=30)
features = [
    float(np.mean(librosa.feature.spectral_centroid(y=y, sr=sr))),
    float(np.mean(librosa.feature.spectral_bandwidth(y=y, sr=sr))),
    float(np.mean(librosa.feature.spectral_rolloff(y=y, sr=sr))),
    float(np.mean(librosa.feature.zero_crossing_rate(y))),
    float(librosa.beat.beat_track(y=y, sr=sr)[0])
]

# Predict EQ
predicted_eq = model.predict([features])[0]

# Print as comma-separated numbers for Java to read
print(",".join([str(round(x, 2)) for x in predicted_eq]))
