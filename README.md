# 🍽️ WebTU Meals (WebTU Repas / وجبات ويب تو)

> **Application Android native pour la réservation intelligente des repas universitaires (ONOU / WebEtu Algérie).**

[![Release](https://img.shields.io/github/v/release/nsavyimanajustin/webtu-app?color=blue&logo=github)](https://github.com/nsavyimanajustin/webtu-app/releases/latest)
[![Platform](https://img.shields.io/badge/platform-Android%207.0%2B-green.svg)](https://developer.android.com)
[![Privacy](https://img.shields.io/badge/privacy-100%25%20Local%20Credentials-brightgreen.svg)](#-sécurité--vie-privée)
[![License](https://img.shields.io/badge/license-MIT-purple.svg)](LICENSE)

---

## 📥 Téléchargement Direct (Pour Étudiants Testeurs)

Téléchargez la dernière version installable sans passer par le Play Store :

👉 **[Télécharger WebTUMeals-v1.0.apk (Dernière version)](https://github.com/nsavyimanajustin/webtu-app/releases/latest/download/WebTUMeals-v1.0.apk)**

Ou accédez à la page des versions : [GitHub Releases](https://github.com/nsavyimanajustin/webtu-app/releases/latest)

---

## ✨ Fonctionnalités Principales

- **⚡ Réservations en 1 Clic & Ligne Unique :**
  - **Petit-déjeuner (Matin)**, **Déjeuner (Midi)** et **Dîner (Soir)** alignés sur une seule ligne ergonomique.
  - Sélection de date intuitive : *Aujourd'hui*, *Demain (Recommandé)*, *Après-demain*.
- **🤖 Mode Auto-Pilote (Jusqu'à 3 Jours) :**
  - **Tout-Auto (3 repas) :** Réserve automatiquement matin, midi et soir pour les 3 prochains jours.
  - **Semi-Auto (Matin & Soir) :** Réserve le petit-déjeuner et le dîner dans votre résidence universitaire, et laisse le midi libre (idéal si vous déjeunez au restaurant du campus).
  - **Auto-réservation à l'ouverture :** Option pour synchroniser et sécuriser automatiquement tous vos repas dès le lancement de l'application.
- **🏢 Support Multi-Restaurants (Résidence vs. Campus) :**
  - Associe automatiquement votre résidence universitaire officielle.
  - Permet de choisir un restaurant secondaire (dépôt campus) pour vos déjeuners de midi.
- **🔄 Mises à Jour Intégrées (In-App Updates) :**
  - Détection automatique des nouvelles versions publiées sur GitHub Releases.
  - Téléchargement et installation directe du nouvel APK sans configuration manuelle.
- **🌍 Trilingue & Support RTL Complet :**
  - Français (FR)
  - Anglais (EN)
  - Arabe (العربية - AR) avec inversion complète de la direction d'affichage (RTL).
- **🔒 Sécurité & Vie Privée 100% Locale :**
  - Vos identifiants WebEtu (Matricule BAC & Mot de passe) restent **exclusivement stockés sur votre smartphone** (chiffrement local Android EncryptedSharedPreferences).
  - Aucun serveur intermédiaire, aucune télémétrie obscure, aucun stockage distant de mot de passe.
  - Communication directe et sécurisée entre votre téléphone et les API officielles de l'ONOU (MESRS).

---

## 🛠️ Stack Technique

- **Langage :** Kotlin 2.2 / JVM 17
- **UI Toolkit :** Jetpack Compose & Material 3
- **Réseau :** OkHttp 4 avec gestion des cookies de session ONOU et sérialisation JSON robuste
- **Architecture :** MVVM (Model-View-ViewModel) + StateFlow unidirectionnel
- **Mises à jour :** GitHub Releases API + Android FileProvider

---

## 🚀 Compiler depuis les sources

```bash
git clone https://github.com/nsavyimanajustin/webtu-app.git
cd webtu-app
./gradlew test
./gradlew assembleRelease
```
L'APK se trouve ensuite dans `app/build/outputs/apk/release/app-release.apk`.
