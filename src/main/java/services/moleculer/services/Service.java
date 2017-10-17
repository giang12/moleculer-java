package services.moleculer.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.datatree.Tree;
import services.moleculer.ServiceBroker;
import services.moleculer.config.MoleculerComponent;

public abstract class Service implements MoleculerComponent {

	// --- LOGGER ---

	protected final Logger logger = LoggerFactory.getLogger(getClass());

	// --- PROPERTIES ---

	protected final String name;

	// --- CONSTRUCTORS ---

	public Service() {
		this(null);
	}

	public Service(String name) {
		if (name == null || name.isEmpty()) {
			Name n = getClass().getAnnotation(Name.class);
			if (n != null) {
				name = n.value();
			}
			if (name != null) {
				name = name.trim();
			}
			if (name == null || name.isEmpty()) {
				name = getClass().getName();
				int i = Math.max(name.lastIndexOf('.'), name.lastIndexOf('$'));
				if (i > -1) {
					name = name.substring(i + 1);
				}
				name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
			}
		}
		this.name = name;
	}

	// --- GET NAME OF SERVICE ---
	
	public final String name() {
		return name;
	}
	
	// --- START SERVICE ---

	/**
	 * Initializes service instance.
	 * 
	 * @param broker
	 *            parent ServiceBroker
	 * @param config
	 *            optional configuration of the current component
	 */
	@Override
	public void start(ServiceBroker broker, Tree config) throws Exception {
	}

	// --- SERVICE CREATED ---

	public void created() throws Exception {
	}

	// --- SERVICE STARTED ---

	public void started() throws Exception {
	}

	// --- STOP SERVICE ---

	@Override
	public void stop() {
	}

}