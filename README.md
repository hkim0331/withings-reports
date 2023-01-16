# Withings-Reports

launch periodically from systemd.

## Required

- babashka

- systemd(linux)
  if macOS, `systemd` can be replaced by `launch daemon` or `launch agent`.

## usage

  % bb -m withings-reports.reports

'-' は '_' にすると動かない。引数はパスではなく、ネームスペース。

## Memo

```
最新の情報
何月何日何時
体重 1, 7, 28日前
筋肉量 76, 1, 7, 28日前
体水分率 77, 1, 7, 28日前

状態指標
体重🔵🔵
体水分率🔵🟡
総合

体重
25日平均の+-1SD🟡 +-2SD🔴
75日平均の+-1SD🟡 +-2SD🔴

体水分率
25日平均の+1SD🟡 +2SD🔴
75日平均の+1SD🟡 +2SD🔴

🔵0🟡1🔴3
 2点以下は青，3-4黄色,5ー赤
```

## Usage
