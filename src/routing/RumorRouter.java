/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package routing;

import core.Connection;
import core.Message;
import core.Settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Epidemic message router with drop-oldest buffer and only single transferring
 * connections at a time.
 */
public class RumorRouter extends ActiveRouter {

	/** Router namespace (where settings are looked up) */
	public static final String ROUTER_NS = "RumorRouter";
	/** routers rng seed */
	public static final String RNG_SEED = "rngSeed";

	/** rng for the rumor router */
	protected static Random rng;

	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public RumorRouter(Settings s) {
		super(s);
		//TODO: read&use epidemic router specific settings (if any)

		//TTL_CHECK_INTERVAL = 0; // TODO necessary?

		// Initialize rng
		if (rng == null) {
			Settings rs = new Settings(ROUTER_NS);
			if (rs.contains(RNG_SEED)) {
				int seed = rs.getInt(RNG_SEED);
				rng = new Random(seed);
			}
			else {
				rng = new Random(0);
			}
		}
	}

	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected RumorRouter(RumorRouter r) {
		super(r);
		//TODO: copy epidemic settings here (if any)
	}

	/**
	 * Tries to send a randomly selected message to any connection
	 * @return The connection that started a transfer or null if no connection
	 *  accepted a message
	 */
	protected Connection tryRandomMessageToAllConnections(){
		List<Connection> connections = getConnections();
		if (connections.size() == 0 || this.getNrofMessages() == 0) {
			return null;
		}

		List<Message> allMessages = new ArrayList<Message>(this.getMessageCollection());
		int rndMsg = rng.nextInt(allMessages.size());

		List<Message> messages = new ArrayList();
		messages.add(allMessages.get(rndMsg));

		//Message m = allMessages.get(rndMsg).replicate();
		//Message n = new Message(m.getFrom(), m.getTo(), "Rumor", m.getSize());
		//n.copyFrom(m);
		//messages.add()

		return tryMessagesToConnections(messages, connections);
	}

	@Override
	public void update() {
		super.update();
		if (isTransferring() || !canStartTransfer()) {
			return; // transferring, don't try other connections yet
		}

		// Try first the messages that can be delivered to final recipient
		if (exchangeDeliverableMessages() != null) {
			return; // started a transfer, don't try others (yet)
		}

		// then try any/all message to any/all connection
		this.tryRandomMessageToAllConnections();
	}


	@Override
	public RumorRouter replicate() {
		return new RumorRouter(this);
	}


}
