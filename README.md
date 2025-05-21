# Websitereader

This app allows reading websites using the system or external TextToSpeech (TTS).

### Usage

Just share a website link to the app using the android share menu.
To use a TTS provider other than the android system tts you have to configure it in the app's
settings.
Websites are simplified using the [Readability4J](https://github.com/dankito/Readability4J)
library (a kotlin port of Mozilla's
Readability.js) before being read.
After the audio synthesization the audio can be played by using the app's own audio player or the
audio file can be saved to the device.

One of the biggest features of this app is that, the audio synthesization & playback can occur in
the background.
You may have to disable battery optimization for this app if you encounter problems.

### Supported TTS Providers

- Android TTS
    - There is a setting in android to change the TTS engine. For most people this will be the
      Google TTS engine by default.
- OpenAI TTS
- All TTS providers that implement a OpenAI TTS style api
    - For example the self-hosted [Kokoro-FastAPI](https://github.com/remsky/Kokoro-FastAPI)

### Api Costs

Disclaimer: I am not responsible for any costs that might occur by using external TTS providers such
as OpenAI.

The app displays a estimate of the cost to synthesize the provided text when using a external TTS
provider.
This estimate is based on cost to synthesize 1 Million characters of text, this cost has to be set
when adding a external TTS provider in the settings.
Many TTS providers however charge you based on the number of tokens to synthesize.
You can use a rough thumb-rule to convert characters to tokens: One token corresponds roughly to 4
characters. ([Source](https://platform.openai.com/tokenizer))

### ToDo

- Make supported languages configurable
- Have a history of last articles read to list on the main activity of the app
    - Maybe also make the output audio files persistent and be playable from the history list
- Add media controls to the share receiver activity (not just the notification)
