# Withings-Reports

## Unreleased
- log の整理: fetch-mea を log/debug に落とすか？
- log level: INFO/DEBUG. telemere?
- wc.kohhoh.jp のアドレスを伏せたい。

## v1.12.141 / 2024-05-23
### Fixed
- systemd/Makefile

## v1.12.136 / 2024-05-23
- systemd EnvironmentFile

## v1.11.131 / 2024-05-23
- gitignored .DS_Store
- `git rm -r --cached .`
### Changed
- reports takes 5th argument, true/false.

## v1.10.123 / 2024-05-23
- `.env` must exist. if not, the systemd service does not start.
- used Environment instead of EnvironmentFile.
  after `git pull` on production(wakato),
  check the `systemd/withings-report.service`,
  then `make install start enable`.

## v1.9.118 / 2024-05-22
- refactored `reports.clj`.

## v1.9.114 / 2024-05-21
### Added
- bump-version.sh
### Changed
- org.babashka/mysql "0.1.2"

## 0.8.1 - 2023-01-23
- https://wc.kohhoh.jp へのリンク

## 0.8.0 - 2023-01-23
### Added
- lp_login: report の前にログインし、認証を受ける。

## 0.7.2 - 2023-01-17
### Fixed
- main のコメント外し忘れ。

## 0.7.1 - 2023-01-17
### Fixed
- 1 のデータが反映していない（hkimura）: withings-cache のデータ取得が間に合ってない。JST か？
- systemd でエラーの原因は、ログに指定したファイルが絶対パスになってないこと。
```
Jan 17 09:00:02 kohhoh systemd[1]: withings-reports.timer: Failed to queue unit startup job: Unit withings-reports.service has a bad unit file setting.
Jan 17 09:00:02 kohhoh systemd[1]: withings-reports.timer: Failed with result 'resources'.

Jan 16 09:00:01 kohhoh.jp systemd[1]: withings-reports.service: Main process exited, code=exited, status=1/FAILURE
Jan 16 09:00:01 kohhoh.jp systemd[1]: withings-reports.service: Failed with result 'exit-code'.
Jan 16 12:55:03 kohhoh.jp systemd[1]: /lib/systemd/system/withings-reports.service:9: StandardOutput= path is not absolute: log/reports.log
```
- ループ中でエラーになった時。
```
        (try
          (send-report user (str report "\n" (help days)))
          (catch Exception e
            (log/info "reports error:" (.getMessage e))))
```

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

## v1.9.114 / 2024-05-21
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
