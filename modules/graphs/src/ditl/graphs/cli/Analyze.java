/*******************************************************************************
 * This file is part of DITL.                                                  *
 *                                                                             *
 * Copyright (C) 2011 John Whitbeck <john@whitbeck.fr>                         *
 *                                                                             *
 * DITL is free software: you can redistribute it and/or modify                *
 * it under the terms of the GNU General Public License as published by        *
 * the Free Software Foundation, either version 3 of the License, or           *
 * (at your option) any later version.                                         *
 *                                                                             *
 * DITL is distributed in the hope that it will be useful,                     *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of              *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the               *
 * GNU General Public License for more details.                                *
 *                                                                             *
 * You should have received a copy of the GNU General Public License           *
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.       *
 *******************************************************************************/
package ditl.graphs.cli;

import java.io.IOException;
import java.util.*;

import org.apache.commons.cli.*;

import ditl.*;
import ditl.Store.NoSuchTraceException;
import ditl.cli.ExportApp;
import ditl.graphs.*;

public class Analyze extends ExportApp {
	
	final static String nodeCountOption = "node-count";
	final static String transitTimesOption = "transit-times";
	final static String timeToFirstContactOption = "first-contact-time";
	final static String numContactsOption = "num-contacts";
	final static String nodeDegreeOption = "node-degree";
	final static String contactsOption = "contacts";
	final static String interContactsOption = "inter-contacts";
	final static String anyContactsOption = "any-contacts";
	final static String interAnyContactsOption = "inter-any-contacts";
	final static String clusteringOption = "clustering";
	final static String numCCOption = "ccs";
	
	private GraphOptions graph_options = new GraphOptions(GraphOptions.PRESENCE, GraphOptions.LINKS, GraphOptions.CC);
	private ReportFactory<?> factory;
	
	public final static String PKG_NAME = "graphs";
	public final static String CMD_NAME = "analyze";
	public final static String CMD_ALIAS = "a";
	
	
	@Override
	protected void initOptions() {
		super.initOptions();
		graph_options.setOptions(options);
		OptionGroup reportGroup = new OptionGroup();
		reportGroup.addOption(new Option(null, nodeCountOption, false, "node count report") );
		reportGroup.addOption(new Option(null, transitTimesOption, false, "transit times report") );
		reportGroup.addOption(new Option(null, timeToFirstContactOption, false, "time to first contact report") );
		reportGroup.addOption(new Option(null, numContactsOption, false, "number of contacs distribution") );
		reportGroup.addOption(new Option(null, nodeDegreeOption, false, "node degree distribution over time") );
		reportGroup.addOption(new Option(null, contactsOption, false, "contact time distribution") );
		reportGroup.addOption(new Option(null, interContactsOption, false, "inter-contact time distribution") );
		reportGroup.addOption(new Option(null, anyContactsOption, false, "any-contact time distribution") );
		reportGroup.addOption(new Option(null, interAnyContactsOption, false, "inter-any-contact time distribution") );
		reportGroup.addOption(new Option(null, clusteringOption, false, "clustering coefficient distribution over time") );
		reportGroup.addOption(new Option(null, numCCOption, false, "distribution of connected component sizes over time") );
		reportGroup.setRequired(true);
		options.addOptionGroup(reportGroup);
	}

	@Override
	protected void parseArgs(CommandLine cli, String[] args)
			throws ParseException, ArrayIndexOutOfBoundsException,
			HelpException {
		
		super.parseArgs(cli, args);
		graph_options.parse(cli);
		
		if ( cli.hasOption(nodeCountOption) ){
			factory = new NodeCountReport.Factory();
		} else if ( cli.hasOption(transitTimesOption) ){
			factory = new TransitTimesReport.Factory();
		} else if ( cli.hasOption(timeToFirstContactOption) ){
			factory = new TimeToFirstContactReport.Factory();
		} else if ( cli.hasOption(numContactsOption) ){
			factory = new NumberContactsReport.Factory();
		} else if ( cli.hasOption(nodeDegreeOption) ){
			factory = new NodeDegreeReport.Factory();
		} else if ( cli.hasOption(contactsOption) ){
			factory = new ContactTimesReport.Factory(true);
		} else if ( cli.hasOption(interContactsOption) ){
			factory = new ContactTimesReport.Factory(false);
		} else if ( cli.hasOption(anyContactsOption) ){
			factory = new AnyContactTimesReport.Factory(true);
		} else if ( cli.hasOption(interAnyContactsOption) ){
			factory = new AnyContactTimesReport.Factory(false);
		} else if ( cli.hasOption(clusteringOption) ){
			factory = new ClusteringCoefficientReport.Factory(true);
		} else if ( cli.hasOption(numCCOption) ){
			factory = new ConnectedComponentsReport.Factory();
		}
	}
	
	
	@Override
	protected void run() throws IOException, NoSuchTraceException {
		Report report = factory.getNew(_out);
		
		Long minTime=null, maxTime=null, incrTime=null;
		List<Reader<?>> readers = new LinkedList<Reader<?>>();
		
		if ( report instanceof PresenceTrace.Handler ){
			PresenceTrace presence = (PresenceTrace)_store.getTrace(graph_options.get(GraphOptions.PRESENCE));
			StatefulReader<PresenceEvent,Presence> presenceReader = presence.getReader();
			
			Bus<PresenceEvent> presenceEventBus = new Bus<PresenceEvent>();
			Bus<Presence> presenceBus = new Bus<Presence>();
			presenceReader.setBus(presenceEventBus);
			presenceReader.setStateBus(presenceBus);
			
			PresenceTrace.Handler ph = (PresenceTrace.Handler)report;
			presenceBus.addListener(ph.presenceListener());
			presenceEventBus.addListener(ph.presenceEventListener());
			
			readers.add(presenceReader);
			
			minTime = presence.minTime();
			maxTime = presence.maxTime();
			incrTime = presence.maxUpdateInterval();
		}		
		
		if ( report instanceof LinkTrace.Handler ){
			LinkTrace links = (LinkTrace)_store.getTrace(graph_options.get(GraphOptions.LINKS));			
			StatefulReader<LinkEvent,Link> linksReader = links.getReader();
			
			Bus<LinkEvent> linkEventBus = new Bus<LinkEvent>();
			Bus<Link> linkBus = new Bus<Link>();
			linksReader.setBus(linkEventBus);
			linksReader.setStateBus(linkBus);

			LinkTrace.Handler lh = (LinkTrace.Handler)report;
			linkBus.addListener(lh.linkListener());
			linkEventBus.addListener(lh.linkEventListener());
			
			readers.add(linksReader);
			
			if ( minTime == null ) minTime = links.minTime();
			if ( maxTime == null ) maxTime = links.maxTime();
			incrTime = links.maxUpdateInterval();
		}
		
		if ( report instanceof ConnectedComponentsTrace.Handler ){
			ConnectedComponentsTrace groups = (ConnectedComponentsTrace)_store.getTrace(graph_options.get(GraphOptions.CC));
			StatefulReader<GroupEvent,Group> groupReader = groups.getReader();
			
			Bus<GroupEvent> groupEventBus = new Bus<GroupEvent>();
			Bus<Group> groupBus = new Bus<Group>();
			groupReader.setBus(groupEventBus);
			groupReader.setStateBus(groupBus);
			
			ConnectedComponentsTrace.Handler gh = (ConnectedComponentsTrace.Handler)report;
			groupEventBus.addListener(gh.groupEventListener());
			groupBus.addListener(gh.groupListener());
			
			readers.add(groupReader);
			
			if ( minTime == null ) minTime = groups.minTime();
			if ( maxTime == null ) maxTime = groups.maxTime();
			incrTime = groups.maxUpdateInterval();
		}

		Runner runner = new Runner(incrTime, minTime, maxTime);
		for ( Reader<?> reader : readers )
			runner.addGenerator(reader);
		runner.run();
		
		report.finish();
	}
}
