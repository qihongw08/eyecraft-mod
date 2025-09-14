import speech_recognition as sr
import sys

def main():
    recognizer = sr.Recognizer()
    microphone = sr.Microphone()
    
    with microphone as source:
        recognizer.adjust_for_ambient_noise(source, duration=1)
    
    try:
        with microphone as source:
            audio = recognizer.listen(source, timeout=5, phrase_time_limit=10)
        
        text = recognizer.recognize_google(audio)
        print(text)  
        sys.stdout.flush()
        
    except sr.WaitTimeoutError:
        print("")  
    except sr.UnknownValueError:
        print("")  
    except sr.RequestError as e:
        print("")
    except Exception as e:
        print("")

if __name__ == "__main__":
    main()
