## Android アプリ
### 概要
複数のアプリ登録者（ランダム）に自身の2択アンケートを投げかけ、制限時間が過ぎるとプッシュ通知を受信し、回答結果を確認できる  
自身が回答者として選出され、回答者になることもある
※バックグラウンド・・・別途「fourth-project」リポジトリ
### 画面一覧
- アカウント登録画面
- メイン画面（タブ4つ）
  - 自身の質問一覧フラグメント
  - 回答者として受信した質問一覧フラグメント
  - 新規質問投稿フラグメント
  - アカウント情報変更フラグメント
- 質問詳細画面
  - 自身の質問詳細画面
  - 回答者として受信した質問詳細画面
### 機能一覧
- アカウント登録、更新機能   
- 質問投稿、受信、詳細確認機能  
- 回答結果受信機能  
- gRPCサーバと通信機能（双方向もあり）  
- gRPC通信をTLSでラップ  
- 画面非同期更新機能  
- サーバとのセッション維持機能  
- 自動ログイン機能  
- Modal機能  
- firebaseプッシュ通知機能  
- 画面入力値バリデーション機能  
- サーバ側DBとネイティブDBの同期  
- ネイティブDB（Room Database）  
- ORマッパー  

