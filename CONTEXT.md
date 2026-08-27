# Family Guard

Ota-ona **Monitoring** qiladi: yosh bola telefonida **Cover** ko‘rinadi, Parent ilovasida holat ko‘rinadi. To‘sish yo‘q — Parent o‘zi tanbeh beradi.

## Language

**Family**:
Ota-ona ro‘yxatdan o‘tganda yuzaga keladigan bitta uy xo‘jaligi. Juftlash va barcha nazorat ma’lumotlari shu birlikka tegishli.
_Avoid_: Account, household (kodda), user

**Parent**:
Oilani yaratadigan va dashboardni ishlatadigan katta yoshdagi odam.
_Avoid_: Admin, ota-ona (inglizcha identifikator o‘rniga), owner

**Child**:
Yosh bola; uning telefonida Cover ishlaydi va ota-ona ko‘radigan holat shu qurilmadan keladi.
_Avoid_: Kid, student, device (odam o‘rniga)

**Cover**:
Bola asosiy ekranida ko‘radigan yagona narsa — ob-havo. Nazorat va chat shu qoplama orqasida.
_Avoid_: Launcher, decoy app, home screen, weather app (mahsulot nomi o‘rniga)

**Pairing code**:
Oilaning 6 xonali kodi; Child shu kod bilan Family ga bog‘lanadi.
_Avoid_: Invite, OTP, PIN (chat PIN bilan aralashtirmaslik)

**Chat PIN**:
Cover ichidagi yashirin chatni ochadigan kod. Pairing code emas.
_Avoid_: Password, pairing code

**Device**:
Family ga bog‘langan jismoniy telefon (bolanikida nazorat agenti yuradi).
_Avoid_: Phone model, handset

**Monitoring**:
Child qurilmasidagi holatni Parent ga ko‘rsatish. Ilova yoki saytni yopish emas.
_Avoid_: Blocking, filter, restriction, kiosk

**Usage**:
Child qaysi ilovani qancha ochiq tutgani. Launcher’dagi o‘rnatilgan ilovalar ham shu yerda, vaqti nol bo‘lsa ham; keyin o‘rnatilgani keyingi sinxronida chiqadi.
_Avoid_: Screen time (glossary atamasi o‘rniga), analytics

**Family Chat**:
Parent va Child o‘rtasidagi ilova ichidagi suhbat (Child’da Cover + Chat PIN orqasida). Telefon SMS’i emas.
_Avoid_: SMS, thread, Telegram (mahsulot nomi o‘rniga)

**SMS**:
Child qurilmasidagi operator xabarlari: boshqalardan kelgani va Child yozgan javob. Parent faqat ko‘radi, o‘zi yozmaydi.
_Avoid_: Family Chat, message (chat ma’nosida), inbox (yo‘nalish o‘rniga butun tushuncha)

**Read receipt**:
Family Chat’da yuboruvchi ko‘radigan holat: bir belgi — yetkazilgan, ikki belgi — o‘qilgan.
_Avoid_: tick, ptichka (glossary o‘rniga), delivered (alohida holat qilib)

**Captured notification**:
Child status barida paydo bo‘lgan bildirishnoma: ilova, sarlavha, matn, vaqt. Parent telefonidagi push emas. Parent bitta qatorni yoki hammasini o‘chirishi mumkin. Bir xil vaqtdagi bir xil nusxa saqlanmaydi.
_Avoid_: Push, FCM, alert, Parent notification (push ma’nosida)
