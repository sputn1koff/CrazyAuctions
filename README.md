# Что было изменено в плагине?

-- 1. Добавлена поддержка кастомных голов Base64. Использование: 
`Item: 'eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzIxZDA5MzBiZDYxZmVhNGNiOTAyN2IwMGU5NGUxM2Q2MjAyOWM1MjRlYTBiMzI2MGM3NDc0NTdiYTFiY2ZhMSJ9fX0='`

<img width="383" height="96" alt="изображение" src="https://github.com/user-attachments/assets/f22b24fe-1c28-4ba1-976d-dd9ef93e088f" />

-- 2. Теперь можно сделать в каких слотах будут вещи игроков. Использование (config.yml): 
```
    DisplaySlots:
      - 0
      - 1
      - 2
      - 3
      - 4
      - 5
      - 6
      - 7
      - 8
```

<img width="253" height="149" alt="изображение" src="https://github.com/user-attachments/assets/1836b4a9-c626-4986-a42e-f1738046f9d3" />

-- 3. Добавлена возможность сделать обводку стеклом. Использование (config.yml): 
```
    Filler_1:
      Item: 'light_blue_stained_glass_pane'
      Name: 'by HexStudio'
      Lore: []
      Slots:
        - 0
        - 9
        - 18
        - 27
        - 36
        - 45
        - 8
        - 17
        - 26
        - 35
        - 44
        - 53

    Filler_2:
      Item: 'blue_stained_glass_pane'
      Name: 'by HexStudio'
      Lore: []
      Slots:
        - 1
        - 7
        - 46
        - 52
```

<img width="359" height="258" alt="изображение" src="https://github.com/user-attachments/assets/2a71a91f-f4c0-4077-b7e3-19ecd9f82bf5" />

-- 4. Добавлена анимация при открытии меню. Использование (config.yml): 
```
  Animation:
    Enabled: true
    Type: "RANDOM" # CENTER, RIGHT, LEFT, TOP_DOWN, BOTTOM_UP, RANDOM
    DelayTicks: 2
    FillerMaterial: "AIR"
    FillerName: ""
    Sound:
      Enabled: true
      Name: "BLOCK_METAL_PLACE"
      Volume: 0.5
      Pitch: 1.5
```

---------------------------------------------------------------------

# Crazy Auctions - Issues tab has been disabled, A rewrite is pending.
Source Code for Crazy Auctions

## Build Status:
[![Build Status](https://jenkins.badbones69.com/view/Stable/job/Crazy-Auctions/badge/icon)](https://jenkins.badbones69.com/view/Stable/job/Crazy-Auctions/)
 
## Latest Version:
[![Latest Version](https://img.shields.io/badge/Latest%20Version-1.2.12-blue)](https://github.com/badbones69/Crazy-Auctions/releases/latest)

## Support:
https://discord.com/invite/MCuz8JG/

## Jenkins: 
[https://jenkins.badbones69.com/job/Crazy-Auctions/](https://jenkins.badbones69.com/view/Stable/job/Crazy-Auctions/)
 
## Nexus:
[https://nexus.badbones69.com/#browse/browse:maven-releases:com%2Fbadbones69%2Fcrazyauctions](https://repo.badbones69.com/#/releases/com/badbones69/crazyauctions/1.2.18-SNAPSHOT)

## Maven:

```xml
<repository>
    <id>crazycrew-repo-releases</id>
    <name>CrazyCrew Team</name>
    <url>https://repo.badbones69.com/releases</url>
</repository>

<dependency>
    <groupId>com.badbones69</groupId>
    <artifactId>crazyauctions</artifactId>
    <version>{Latest Version}</version>
</dependency>
```
