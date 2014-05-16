package org.powerbot.bot;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.powerbot.gui.BotChrome;
import org.powerbot.gui.BotLauncher;
import org.powerbot.script.Bot;

public class SelectiveEventQueue extends EventQueue {
	private static final SelectiveEventQueue instance = new SelectiveEventQueue();
	private final AtomicBoolean blocking;
	private final AtomicReference<InputSimulator> engine;
	private final AtomicReference<EventCallback> callback;
	private final AtomicReference<Component> component;

	private SelectiveEventQueue() {
		blocking = new AtomicBoolean(false);
		engine = new AtomicReference<InputSimulator>(null);
		callback = new AtomicReference<EventCallback>(null);
		component = new AtomicReference<Component>(null);
	}

	public static SelectiveEventQueue getInstance() {
		return instance;
	}

	public static void pushSelectiveQueue() {
		if (!isPushed()) {
			Toolkit.getDefaultToolkit().getSystemEventQueue().push(instance);
			if (!isPushed()) {
				throw new RuntimeException();
			}
		}
	}

	private static boolean isPushed() {
		return Toolkit.getDefaultToolkit().getSystemEventQueue() instanceof SelectiveEventQueue;
	}

	public boolean isBlocking() {
		return blocking.get();
	}

	public void setBlocking(final boolean blocking) {
		this.blocking.set(blocking);
		if (!blocking) {
			final InputSimulator e = engine.get();
			if (e != null) {
				e.destroy();
			}
			engine.set(null);
		} else {
			final InputSimulator e = engine.get();
			final Component component = this.component.get();
			if (e == null && component != null) {
				engine.set(new InputSimulator(component));
			}
			pushSelectiveQueue();
		}
	}

	public InputSimulator getEngine() {
		return engine.get();
	}

	public Component getComponent() {
		return component.get();
	}

	public void target(final Component component, final EventCallback callback) {
		final InputSimulator engine = this.engine.get();
		final Component c = engine != null ? engine.getComponent() : null;
		if (c == component) {
			return;
		}
		final boolean b = isBlocking();
		setBlocking(false);
		this.component.set(component);
		this.callback.set(callback);
		final BotLauncher launcher = BotLauncher.getInstance();
		if (b) {
			this.engine.set(new InputSimulator(component));
			setBlocking(true);
			launcher.window.get().requestFocusInWindow();
		}
	}

	@Override
	protected final void dispatchEvent(final AWTEvent event) {
		if (event instanceof RawAWTEvent) {
			final AWTEvent e = ((RawAWTEvent) event).getEvent();
			((Component) e.getSource()).dispatchEvent(e);
			return;
		}

		final BotLauncher launcher = BotLauncher.getInstance();
		final Component component = this.component.get();
		final Object t = event.getSource();
		if (t == launcher.overlay.get()) {
			event.setSource(component);
		}

		if (event instanceof MouseEvent || event instanceof KeyEvent) {
			final Bot<?> bot = launcher.bot.get();
			if (bot != null && bot.dispatcher != null) {
				bot.dispatcher.dispatch(event);
			}
		}
		final Object s = event.getSource();
		if (!blocking.get() || s != component ||
				!(event instanceof MouseEvent || event instanceof KeyEvent ||
						event instanceof WindowEvent || event instanceof FocusEvent)) {
			super.dispatchEvent(event);
			return;
		}
		final EventCallback callback = this.callback.get();
		if (callback != null) {
			callback.execute(event);
		}
	}

	public static final class RawAWTEvent extends AWTEvent {
		private static final long serialVersionUID = -1409783285345666039L;
		private final AWTEvent event;

		public RawAWTEvent(final AWTEvent event) {
			super(event.getSource(), event.getID());
			this.event = event;
		}

		public AWTEvent getEvent() {
			return event;
		}
	}

	public static interface EventCallback {
		public void execute(final AWTEvent event);
	}
}
