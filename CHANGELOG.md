# Withings-Reports

## Unreleased
- bump-version.sh

## 0.6.2 - 2023-01-15
- log の整理
- lack icon の選択 😨, 😱, 😰, 🌚, 💤, 🤢, 👻, 👎, 
## 0.6.1 - 2023-01-15
on kohhou with VScode remote ssh
- updated systemd/Makefile
- create /.env on kohhoh
- gitignored /.env

## 0.6.0 - 2023-01-15
- kohhou にデプロイ、テスト
### Added
- systemd/Makefile

## 0.5.1 - 2023-01-15
- reports に optional 引数。あれば送信しない。
```
(defn reports
  "(format-report) の戻り値にヘルプメッセージを出して送信。
   nosend をつけて呼ぶと送信しない。"
  [users types days days2 & nosend]
```

## 0.5.0 - 2023-01-15
- 25日間SD、75日間SD を求めた。欠測の場合は "-" とした。

## 0.4.0 - 2023-01-15
- データの持ち方をマップに変えた。こっちの方が格段にデバッグしやすい。

## 0.3.1 - 2023-01-14
### Changed
- fetch-data => fetch-average
### Added
- fetch-sd

## 0.3.0 - 2023-01-14
### Changed
- fetch-meas はキーワード引数を取る。
```
(defn fetch-meas [{:keys [id type days]}]...)
```

## 0.2.4-SNAPSHOT
- make-report

## 0.2.3 - 2023-01-03
- success line push
```
(16 (1 [1 "none"] [25 "none"] [75 93.63])
    (5 [1 "none"] [25 "none"] [75 "none"])
    (77 [1 "none"] [25 "none"] [75 "none"]))
```

## 0.2.2 - 2023-01-02
- defined send-report

## 0.1.1 - 2023-01-01
- branch feature-ishige
- make-report

## 0.1.0 - 2022-12-31
- project started.
