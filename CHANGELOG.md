# Withings-Reports

## Unreleased
- bump-version.sh
- could not find namespace: main
```
Jan 16 09:00:01 kohhoh systemd[1]: Started Send Withings repots to Saga athletes.
Jan 16 09:00:01 kohhoh bb[2467665]: ----- Error --------------------------------------------------------------------
Jan 16 09:00:01 kohhoh bb[2467665]: Type:     java.lang.Exception
Jan 16 09:00:01 kohhoh bb[2467665]: Message:  Could not find namespace: main.
Jan 16 09:00:01 kohhoh bb[2467665]: Location: <expr>:1:10
Jan 16 09:00:01 kohhoh bb[2467665]: ----- Context ------------------------------------------------------------------
Jan 16 09:00:01 kohhoh bb[2467665]: 1: (ns user (:require [main])) (apply main/-main *command-line-args*)
Jan 16 09:00:01 kohhoh bb[2467665]:             ^--- Could not find namespace: main.
Jan 16 09:00:01 kohhoh bb[2467665]: ----- Stack trace --------------------------------------------------------------
Jan 16 09:00:01 kohhoh bb[2467665]: user - <expr>:1:10
Jan 16 09:00:01 kohhoh systemd[1]: withings-reports.service: Main process exited, code=exited, status=1/FAILURE
Jan 16 09:00:01 kohhoh systemd[1]: withings-reports.service: Failed with result 'exit-code'.
Jan 16 09:00:01 kohhoh CRON[2467662]: (CRON) info (No MTA installed, discarding output)
```
- doseq を pmap で並列化する(ユーザ増えたらでいい)

## 0.7.0 - 2023-01-16
## Added
- /log/.placeholder
  withings-reports.service の [service] セクションに以下を追加

```
  StandardOutput=append:log/reports.log
  StandardError=append:log/reports.log
```
## Fixed
- 不変分散だと分母がn-1でゼロワリ発生するケースが増える
  => try~catch で捕まえ 0 を返す。
- reports.clj スクリプトの実行の仕方。
  bb/withings_reports/reports.clj とし、

  % bb -m withings-reports.reports

  これで、withings-reports.reports ネームスペースの -main 関数を起動する。

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
