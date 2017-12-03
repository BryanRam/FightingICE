package fighting;

import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;

import command.CommandTable;
import enumerate.Action;
import enumerate.State;
import image.Image;
import input.KeyData;
import manager.GraphicManager;
import setting.FlagSetting;
import setting.GameSetting;
import setting.LaunchSetting;
import struct.AttackData;
import struct.CharacterData;
import struct.FrameData;

public class Fighting {

	protected Character[] playerCharacters;

	protected Deque<LoopEffect> projectileDeque;

	private Deque<KeyData> inputCommands;

	private LinkedList<LinkedList<HitEffect>> hitEffects;

	protected CommandTable commandTable;

	public Fighting() {
		this.playerCharacters = new Character[2];
		this.projectileDeque = new LinkedList<LoopEffect>();
		this.inputCommands = new LinkedList<KeyData>();
		this.commandTable = new CommandTable();
		this.hitEffects = new LinkedList<LinkedList<HitEffect>>();

	}

	public void initialize() {
		for (int i = 0; i < 2; i++) {
			this.playerCharacters[i] = new Character();
			this.playerCharacters[i].initialize(LaunchSetting.characterNames[i], i == 0);
			this.hitEffects.add(new LinkedList<HitEffect>());
		}

		this.projectileDeque = new LinkedList<LoopEffect>();
		this.inputCommands = new LinkedList<KeyData>();

		///// 旧Fighting処理内容/////

		// BGMのロード
		// SEロード
		// 画像系ロード←Launcherでやってる
		// スクリーン画像取得←ここでやる
		// 背景画像ロード←Launcherでやってる
		// スコア・経過時間の結果を格納する配列初期化←Playでやってる
		// 波動拳格納リスト初期化←ここ
		// コマンド格納リスト初期化←ここ
		// リプレイ用ファイルオープン←ここかPlay
		// Json用ファイルオープン←未定

	}

	public void processingFight(int currentFrame, KeyData keyData) {

		// 1. コマンドの実行・対戦処理
		processingCommands(currentFrame, keyData);
		// 2. 当たり判定の処理
		calculationHit(currentFrame);
		// 3. 攻撃パラメータの更新
		updateAttackParameter();
		// 4. キャラクター情報の更新
		updateCharacter();

	}

	/** 入力されたキーを基にアクションを実行する */
	protected void processingCommands(int currentFrame, KeyData keyData) {
		this.inputCommands.addLast(keyData);

		if (this.inputCommands.size() > GameSetting.INPUT_LIMIT) {
			this.inputCommands.removeFirst();
		}

		for (int i = 0; i < 2; i++) {
			if (!this.inputCommands.isEmpty()) {
				// Action executeAction =
				// this.commandTable.convertKeyToAction(this.playerCharacters[i],this.inputCommands);

				Action executeAction = this.commandTable.interpretationCommandFromKeyData(this.playerCharacters[i],
						this.inputCommands);
				if (ableAction(this.playerCharacters[i], executeAction)) {
					this.playerCharacters[i].runAction(executeAction, true);
				}
			}
		}
	}

	/** 攻撃の当たり判定と,それに伴うキャラクターのパラメータ・コンボ状態の更新を行う */
	protected void calculationHit(int currentFrame) {
		boolean[] isHit = { false, false };

		// 波動拳の処理
		int dequeSize = this.projectileDeque.size();
		for (int i = 0; i < dequeSize; i++) {
			LoopEffect projectile = this.projectileDeque.removeFirst();
			int opponentIndex = projectile.getAttack().isPlayerNumber() ? 1 : 0;

			if (detectionHit(this.playerCharacters[opponentIndex], projectile.getAttack())) {
				int myIndex = opponentIndex == 0 ? 1 : 0;
				this.playerCharacters[opponentIndex].hitAttack(this.playerCharacters[myIndex], projectile.getAttack(),
						currentFrame);

			} else {
				this.projectileDeque.addLast(projectile);
			}
		}

		// 通常攻撃の処理
		for (int i = 0; i < 2; i++) {
			int opponentIndex = i == 0 ? 1 : 0;
			Attack attack = this.playerCharacters[i].getAttack();

			if (detectionHit(this.playerCharacters[opponentIndex], attack)) {
				isHit[i] = true;
				// HP等のパラメータの更新
				this.playerCharacters[opponentIndex].hitAttack(this.playerCharacters[i], attack, currentFrame);
			}
		}

		// エフェクト関係の処理

		for (int i = 0; i < 2; i++) {
			if (FlagSetting.enableWindow) {
				if (this.playerCharacters[i].getAttack() != null) {
					// 現在のコンボに応じたエフェクトをセット
					int comboState = Math.max(this.playerCharacters[i].getHitCount() - 1, 0);
					// 4Hit以上であれば,エフェクトは4ヒット目のもの固定
					comboState = Math.min(comboState, 3);

					Image[] effect = GraphicManager.getInstance().getHitEffectImageContaier()[comboState];
					this.hitEffects.get(i).add(new HitEffect(this.playerCharacters[i].getAttack(), effect, isHit[i]));

					// アッパーの処理
					if (playerCharacters[i].getAction() == Action.STAND_F_D_DFB) {
						Image[] upper = GraphicManager.getInstance().getUpperImageContainer()[i];
						Motion motion = this.playerCharacters[i].getMotionList().get(Action.STAND_F_D_DFB.ordinal());

						if (this.playerCharacters[i].startActive(motion)) {
							this.hitEffects.get(i)
									.add(new HitEffect(this.playerCharacters[i].getAttack(), upper, true, false));
						}
					}
				}
			}

			if (isHit[i]) {
				this.playerCharacters[i].setHitConfirm(true);
				this.playerCharacters[i].destroyAttackInstance();
			}

			if (!playerCharacters[i].isComboValid(currentFrame)) {
				playerCharacters[i].setHitCount(0);
			}
		}
	}

	/**
	 * 攻撃オブジェクトのパラメータ更新を行う.
	 */
	protected void updateAttackParameter() {
		// update coordinate of Attacks(long distance)
		int dequeSize = this.projectileDeque.size();
		for (int i = 0; i < dequeSize; i++) {

			// if attack's nowFrame reach end of duration, remove it.
			LoopEffect projectile = this.projectileDeque.removeFirst();
			if (projectile.getAttack().updateProjectileAttack()) {
				projectile.update();
				this.projectileDeque.addLast(projectile);
			}
		}

		// update coordinate of Attacks(short distance)
		for (int i = 0; i < 2; ++i) {
			if (this.playerCharacters[i].getAttack() != null) {
				if (!this.playerCharacters[i].getAttack().update(this.playerCharacters[i])) {
					this.playerCharacters[i].destroyAttackInstance();
				}
			}
		}
	}

	/**
	 * キャラクターのパラメータや波動拳の情報を更新する
	 */
	protected void updateCharacter() {
		for (int i = 0; i < 2; ++i) {
			// update each character.
			this.playerCharacters[i].update();

			// enque object attack if the data is missile decision
			if (this.playerCharacters[i].getAttack() != null) {
				if (this.playerCharacters[i].getAttack().isProjectile()) {

					Attack attack = this.playerCharacters[i].getAttack();
					ArrayList<Image> projectileImage = FlagSetting.enableWindow
							? GraphicManager.getInstance().getProjectileImageContainer() : null;
					if (this.playerCharacters[i].getAction() == Action.STAND_D_DF_FC) {
						projectileImage = FlagSetting.enableWindow
								? GraphicManager.getInstance().getUltimateAttackImageContainer() : null;
					}

					Image[] temp;
					if (FlagSetting.enableWindow) {
						temp = new Image[projectileImage.size()];
						for (int j = 0; j < temp.length; j++) {
							temp[j] = projectileImage.get(j);
						}
					} else {
						temp = null;
					}
					this.projectileDeque.addLast(new LoopEffect(attack, temp));
					this.playerCharacters[i].destroyAttackInstance();
				}
			}

			// change player's direction
			if (playerCharacters[i].isControl()) {
				playerCharacters[i].frontDecision(playerCharacters[i == 0 ? 1 : 0].getHitAreaCenterX());
			}

			// エフェクトの更新
			for (int j = 0; j < this.hitEffects.get(i).size(); j++) {
				if (!this.hitEffects.get(i).get(j).update()) {
					this.hitEffects.get(i).remove(j);
					--j;
				}
			}
		}
		// run pushing effect
		detectionPush();
		// run collision of first and second character.
		detectionFusion();
		// run effect when character's are in the end of stage.
		decisionEndStage();
	}

	/**
	 * 各キャラクターの現在の水平方向のスピード量に応じて, プッシュ処理を行う
	 */
	protected void detectionPush() {
		// whether the conflict of first and second player or not?
		if (isCollision()) {
			/*
			 * int direction = this.playerCharacters[0].isFront() ? 1 : -1; int
			 * p1SpeedX = direction * this.playerCharacters[0].getSpeedX(); int
			 * p2SpeedX = -direction * this.playerCharacters[1].getSpeedX();
			 */
			int p1SpeedX = Math.abs(this.playerCharacters[0].getSpeedX());
			int p2SpeedX = Math.abs(this.playerCharacters[1].getSpeedX());

			if (p1SpeedX > p2SpeedX) {

				this.playerCharacters[1]
						.moveX(this.playerCharacters[0].getSpeedX() - this.playerCharacters[1].getSpeedX());

			} else if (p1SpeedX < p2SpeedX) {
				this.playerCharacters[0]
						.moveX(this.playerCharacters[1].getSpeedX() - this.playerCharacters[0].getSpeedX());

			} else {
				this.playerCharacters[0].moveX(this.playerCharacters[1].getSpeedX());
				this.playerCharacters[1].moveX(this.playerCharacters[0].getSpeedX());
			}
		}
	}

	/**
	 * 相手と位置が重なってしまった場合, 重ならないように各キャラクターの座標の更新処理を行う
	 */
	protected void detectionFusion() {
		// whether the conflict of first and second player or not?
		if (isCollision()) {
			int direction = 0;

			// if first player is left
			if (this.playerCharacters[0].getHitAreaCenterX() < this.playerCharacters[1].getHitAreaCenterX()) {
				direction = 1;
				// if second player is left
			} else if (this.playerCharacters[0].getHitAreaCenterX() > this.playerCharacters[1].getHitAreaCenterX()) {
				direction = -1;
			} else {
				if (this.playerCharacters[0].isFront()) {
					direction = 1;
				} else {
					direction = -1;
				}
			}
			this.playerCharacters[0].moveX(-direction * 2);
			this.playerCharacters[1].moveX(direction * 2);
		}
	}

	/** 相手キャラクターとぶつかっている状態かを判定する */
	private boolean isCollision() {
		return this.playerCharacters[0].getHitAreaLeft() <= this.playerCharacters[1].getHitAreaRight()
				&& this.playerCharacters[0].getHitAreaTop() <= this.playerCharacters[1].getHitAreaBottom()
				&& this.playerCharacters[0].getHitAreaRight() >= this.playerCharacters[1].getHitAreaLeft()
				&& this.playerCharacters[0].getHitAreaBottom() >= this.playerCharacters[1].getHitAreaTop();
	}

	/**
	 * ステージ端からキャラクターがはみ出ないように, 各キャラクターの座標の更新処理を行う
	 */
	protected void decisionEndStage() {

		for (int i = 0; i < 2; ++i) {
			// if action is down, character will be rebound.
			// first player's effect
			if (playerCharacters[i].getHitAreaRight() > GameSetting.STAGE_WIDTH) {
				if (playerCharacters[i].getAction() == Action.DOWN) {
					playerCharacters[i].reversalSpeedX();
				}

				playerCharacters[i].moveX(-playerCharacters[i].getHitAreaRight() + GameSetting.STAGE_WIDTH);

			} else if (playerCharacters[i].getHitAreaLeft() < 0) {
				if (playerCharacters[i].getAction() == Action.DOWN) {
					playerCharacters[i].reversalSpeedX();
				}

				playerCharacters[i].moveX(-playerCharacters[i].getHitAreaLeft());
			}
		}
	}

	/** 入力されたアクションが実行可能かどうかを返す */
	protected boolean ableAction(Character character, Action nextAction) {
		Motion nextMotion = character.getMotionList().get(nextAction.ordinal());
		Motion nowMotion = character.getMotionList().get(character.getAction().ordinal());

		if (character.getEnergy() < -nextMotion.getAttackStartAddEnergy()) {
			return false;
		} else if (character.isControl()) {
			return true;
		} else {
			boolean checkFrame = nowMotion.getCancelAbleFrame() <= nowMotion.getFrameNumber()
					- character.getRemainingFrame();
			boolean checkAction = nowMotion.getCancelAbleMotionLevel() >= nextMotion.getMotionLevel();

			return character.isHitConfirm() && checkFrame && checkAction;
		}
	}

	/**
	 * 攻撃が相手に当たったかどうかを判定する
	 *
	 * @param opponent
	 *            相手キャラクター.
	 * @param attack
	 *            自身が出した攻撃.
	 * @return <em>True</em> 攻撃が当たった <em>False</em> 攻撃が当たらなかった
	 *
	 * @see Character
	 * @see Attack
	 */
	protected boolean detectionHit(Character opponent, Attack attack) {
		if (attack == null || opponent.getState() == State.DOWN) {
			return false;
		} else if (opponent.getHitAreaLeft() <= attack.getCurrentHitArea().getRight()
				&& opponent.getHitAreaRight() >= attack.getCurrentHitArea().getLeft()
				&& opponent.getHitAreaTop() <= attack.getCurrentHitArea().getBottom()
				&& opponent.getHitAreaBottom() >= attack.getCurrentHitArea().getTop()) {
			return true;
		} else {
			return false;
		}
	}

	/** P1, P2のキャラクター情報が格納された配列を返す */
	public Character[] getCharacters() {
		return this.playerCharacters.clone();
	}

	/**
	 * 現在のフレームにおけるゲーム情報を格納したフレームデータを作成する<br>
	 * 両キャラクターの情報, 現在のフレーム数, 現在のラウンド, 波動拳の情報を格納したリスト, 両キャラクターのキー情報, 画面のピクセル情報,
	 * 画面のBufferedImage
	 */
	public FrameData createFrameData(int nowFrame, int round, KeyData keyData) {
		CharacterData[] characterData = new CharacterData[] { new CharacterData(playerCharacters[0]),
				new CharacterData(playerCharacters[1]) };

		Deque<AttackData> newAttackDeque = new LinkedList<AttackData>();
		for (LoopEffect loopEffect : this.projectileDeque) {
			newAttackDeque.addLast(new AttackData(loopEffect.getAttack()));
		}

		return new FrameData(characterData, nowFrame, round, newAttackDeque, keyData);
	}

	/** ラウンド開始時にキャラクター情報を初期化し, リストの中身をクリアーする */
	public void initRound() {
		for (int i = 0; i < 2; i++) {
			this.playerCharacters[i].roundInit();
			this.hitEffects.get(i).clear();
		}

		this.projectileDeque.clear();
		this.inputCommands.clear();

	}

	public LinkedList<LinkedList<HitEffect>> getHitEffectList() {
		return new LinkedList<LinkedList<HitEffect>>(this.hitEffects);
	}

	public Deque<LoopEffect> getProjectileDeque() {
		return new LinkedList<LoopEffect>(this.projectileDeque);
	}
}
