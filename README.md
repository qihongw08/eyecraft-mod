# EyeCraft

a Minecraft accessibility mod that introduces **head-tracking, facial-expression recognition, and voice commands** as alternative input methods.  
___
## Inspiration  
[We can do better.](https://www.youtube.com/watch?v=VoVj7RXt8p4)  

Gaming should be accessible to everyone, regardless of physical limitations. Traditional controllers can create barriers for players with severe mobility or visual impairments, limiting their ability to experience games like *Minecraft*.  

**EyeCraft** was inspired by the vision of an inclusive gaming future—one where players can craft, explore, and survive using only their **eyes, facial expressions, and voice**. By combining computer vision and speech recognition, we aim to break down barriers and make *Minecraft* playable for users with severe disabilities—without altering the way the game looks or feels.  

___

## Contributor
- Troy Gunawardene
- Xiaole Su
- Qihong Wu
- Tomas D'Avola

___

## What It Does  
**EyeCraft** is a Minecraft accessibility mod that introduces **head-tracking, facial-expression recognition, and voice commands** as alternative input methods.  

### Controls
- **Movement & Perspective** → Rotate head up/down/left/right to look around  
- **Forward** → Mouth **“O”**  
- **Jump** → [Custom facial expression]  
- **Mouse Clicks** → Left wink = left click, right wink = right click  
- **Inventory & Hotbar**:  
  - Tilt head left/right → switch hotbar slots  
  - Mouth **“E”** → open inventory  
  - Voice commands → item selection and crafting when inventory is open  

This creates a **hands-free Minecraft experience** that preserves familiar gameplay while opening it up to entirely new players.  

---

## How We Built It  
EyeCraft integrates multiple technologies across computer vision, AI/ML, and modding:  

- **MediaPipe** → facial landmark detection & mesh extraction  
- **OpenCV** → real-time video stream processing  
- **JAX/Flax** → neural network for facial expression classification  
- **Custom-trained model** → accurate head movement recognition  
- **FabricAPI** → Minecraft modding framework for custom input handling  

The system runs **Python-based computer vision** in parallel with a **Java Minecraft mod**, creating a seamless bridge between **human gestures** and **in-game actions**.  

---

## Individual Contributions  
- **Qihong** → Speech detection, player movement system  
- **Troy** → Inventory integration, crafting logic  
- **Tomas** → Head movement model, threading, hotbar management  
- **Xiaole** → Facial expression parsing and gesture mapping  

---

## Challenges  
- No readily available models for detailed facial-expression-to-action mapping  
- Large variations in face shapes and proportions required custom normalization  
- Overlapping expressions (e.g., *Mouth O* vs. *Raising eyebrow*) caused unintended triggers  
- Sparse documentation on Minecraft modding frameworks  

---

## Accomplishments  
- Accurate head-tracking camera movement using our trained model  
- First-time speech-to-text integration for Minecraft controls  
- Functional crafting system operated entirely through accessibility inputs  
- Robust facial expression calculation pipeline  

---

## What We Learned  
> *“It’s hard to play a game using just your face.”* – Xiaole  
>  
> *“First project with real-time vision and face tracking!”* – Troy  
>  
> *“Move slow, break slow.”* – Tomas  
>  
> *“It’s about the journey that matters.”* – Qihong  

---

## What’s Next  
- Improve calibration and expression accuracy  
- Expand facial expression recognition for more granular control  
- Multiplayer accessibility support  
- Full playthrough potential—**EyeCraft players shall beat the Ender Dragon using only face and voice**  

---
