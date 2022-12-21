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
	/** mutation probability setting */
	public static final String MUT_PROB_S = "mutationProbability";
	/** routers rng seed */
	public static final String RNG_SEED = "rngSeed";

	/** rng for the rumor router */
	protected static Random rng;
	/** mutation count */
	protected static int mut_count;
	/** mutation probability */
	protected static double mut_prob;

	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public RumorRouter(Settings s) {
		super(s);

		//TTL_CHECK_INTERVAL = 0; // TODO necessary?

		Settings rs = new Settings(ROUTER_NS);

		// Initialize rng
		if (rng == null) {
			if (rs.contains(RNG_SEED)) {
				int seed = rs.getInt(RNG_SEED);
				rng = new Random(seed);
			}
			else {
				rng = new Random(0);
			}
		}

		// Settings and defaults
		this.mut_count = 0;

		if (rs.contains(MUT_PROB_S)) {
			this.mut_prob = rs.getDouble(MUT_PROB_S);
		} else {
			this.mut_prob = 0.0;
		}

	}

	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected RumorRouter(RumorRouter r) {
		super(r);
		this.rng = r.rng;
		this.mut_prob = r.mut_prob;
	}

	/**
	 * Checks whether a rumor should mutate or not, based on the mutation probability.
	 * @return True if a rumor should mutate, false otherwise.
	 */
	protected boolean shouldMutateRumor() {
		return rng.nextDouble() < mut_prob;
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
		int rndMsgIdx = rng.nextInt(allMessages.size());
		final Message rndMsg = allMessages.get(rndMsgIdx);

		List<Message> messages = new ArrayList();

		if (shouldMutateRumor())
		{
			// Mutate random rumor message
			String new_id = "R" + (1 + ++mut_count);
			Message n = new Message(this.getHost(), rndMsg.getTo(), new_id, rndMsg.getSize());
			//n.copyFrom(m);
			messages.add(n);
		} else {
			// Just spread random rumor message
			messages.add(rndMsg);
		}

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
