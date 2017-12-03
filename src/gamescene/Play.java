package gamescene;

import static org.lwjgl.glfw.GLFW.*;

import java.io.DataOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import enumerate.GameSceneName;
import fighting.Fighting;
import informationcontainer.RoundResult;
import input.KeyData;
import input.Keyboard;
import loader.ResourceLoader;
import manager.GraphicManager;
import manager.InputManager;
import manager.SoundManager;
import setting.FlagSetting;
import setting.GameSetting;
import struct.FrameData;
import struct.GameData;
import struct.ScreenData;
import util.LogWriter;
import util.ResourceDrawer;

public class Play extends GameScene {

	private Fighting fighting;

	private int nowFrame;

	private int elapsedBreakTime;

	private int currentRound;

	private boolean roundStartFlag;

	private FrameData frameData;

	private ScreenData screenData;

	private KeyData keyData;

	private ArrayList<RoundResult> roundResults;

	private DataOutputStream dos;

	private String timeInfo;

	public Play() {
		// 以下4行の処理はgamesceneパッケージ内クラスのコンストラクタには必ず含める
		this.gameSceneName = GameSceneName.PLAY;
		this.isGameEndFlag = false;
		this.isTransitionFlag = false;
		this.nextGameScene = null;
		//////////////////////////////////////
	}

	@Override
	public void initialize() {
		InputManager.getInstance().setSceneName(GameSceneName.PLAY);

		this.fighting = new Fighting();
		this.fighting.initialize();

		this.nowFrame = 0;
		this.elapsedBreakTime = 0;
		this.currentRound = 1;
		this.roundStartFlag = true;

		this.frameData = new FrameData();
		this.screenData = new ScreenData();
		this.keyData = new KeyData();
		this.roundResults = new ArrayList<RoundResult>();

		if (!FlagSetting.trainingModeFlag) {
			openReplayFile();
		}

		GameData gameData = new GameData(fighting.getCharacters());

		InputManager.getInstance().createAIcontroller();
		InputManager.getInstance().startAI(gameData);

		if (FlagSetting.enableWindow && !FlagSetting.muteFlag) {
			SoundManager.getInstance().play(SoundManager.getInstance().getBackGroundMusic());
		}
	}

	@Override
	public void update() {

		if (this.currentRound <= GameSetting.ROUND_MAX) {
			// ラウンド開始時に初期化
			if (this.roundStartFlag) {
				initRound();

			} else if (this.elapsedBreakTime < GameSetting.BREAKTIME_FRAME_NUMBER) {
				// break time
				processingBreakTime();
				this.elapsedBreakTime++;

			} else {
				// processing
				processingGame();
				this.nowFrame++;
			}

		} else {
			Logger.getAnonymousLogger().log(Level.INFO, "Game over");
			// BGMを止める
			if (FlagSetting.enableWindow && !FlagSetting.muteFlag) {
				SoundManager.getInstance().stop(SoundManager.getInstance().getBackGroundMusic());
			}

			Result result = new Result(this.roundResults, this.timeInfo);
			this.setTransitionFlag(true);
			this.setNextGameScene(result);
		}

		if (Keyboard.getKeyDown(GLFW_KEY_ESCAPE)) {
			// BGMを止める
			SoundManager.getInstance().stop(SoundManager.getInstance().getBackGroundMusic());

			HomeMenu homeMenu = new HomeMenu();
			this.setTransitionFlag(true); // 現在のシーンからの遷移要求をtrueに
			this.setNextGameScene(homeMenu); // 次のシーンをセットする
		}

	}

	private void initRound() {
		this.fighting.initRound();
		this.nowFrame = 0;
		this.roundStartFlag = false;
		this.elapsedBreakTime = 0;

		// Input clear
	}

	private void processingBreakTime() {
		// ダミーフレームをAIにセット
		InputManager.getInstance().setFrameData(new FrameData(), new ScreenData());

		if (FlagSetting.enableWindow) {
			GraphicManager.getInstance().drawQuad(0, 0, GameSetting.STAGE_WIDTH, GameSetting.STAGE_HEIGHT, 0, 0, 0, 0);
			GraphicManager.getInstance().drawString("Waiting for Round Start", 350, 200);
		}
	}

	private void processingGame() {
		this.keyData = new KeyData(InputManager.getInstance().getKeyData());

		this.fighting.processingFight(this.nowFrame, this.keyData);
		this.frameData = this.fighting.createFrameData(this.nowFrame, this.currentRound, this.keyData);
		this.screenData = new ScreenData();

		// AIにFrameDataをセット
		InputManager.getInstance().setFrameData(this.frameData, this.screenData);
		// 体力が0orタイムオーバーならラウンド終了処理
		if (isBeaten() || isTimeOver()) {
			processingRoundEnd();
		}

		// リプレイログ吐き出し
		if (!FlagSetting.trainingModeFlag) {
			LogWriter.getInstance().outputLog(this.dos, this.keyData, this.fighting.getCharacters());
		}
		// 画面をDrawerクラスで描画
		if (FlagSetting.enableWindow) {
			ResourceDrawer.getInstance().drawResource(this.fighting.getCharacters(), this.fighting.getProjectileDeque(),
					this.fighting.getHitEffectList(), this.screenData.getScreenImage(),
					this.frameData.getRemainingTimeMilliseconds(), this.currentRound);
		}

	}

	private void processingRoundEnd() {
		RoundResult roundResult = new RoundResult(this.frameData);
		this.roundResults.add(roundResult);

		// AIに結果を渡す sendRoundResult(p1Hp, p2Hp, frames);
		InputManager.getInstance().sendRoundResult(roundResult);
		this.currentRound++;
		this.roundStartFlag = true;
	}

	private boolean isBeaten() {
		return FlagSetting.limitHpFlag
				&& (this.frameData.getCharacter(true).getHp() <= 0 || this.frameData.getCharacter(false).getHp() <= 0);
	}

	private boolean isTimeOver() {
		if (FlagSetting.trainingModeFlag) {
			return this.nowFrame == Integer.MAX_VALUE;
		} else {
			return this.nowFrame == GameSetting.ROUND_FRAME_NUMBER - 1;
		}

	}

	private void openReplayFile() {
		this.timeInfo = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd-HH.mm.ss", Locale.ENGLISH));

		String fileName = LogWriter.getInstance().createOutputFileName("./log/replay/", this.timeInfo);
		this.dos = ResourceLoader.getInstance().openDataOutputStream(fileName + ".dat");

		LogWriter.getInstance().writeHeader(this.dos);
	}

	@Override
	public void close() {
		this.fighting = null;
		this.frameData = null;
		this.screenData = null;
		this.keyData = null;
		this.roundResults.clear();

		try {
			this.dos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

}
