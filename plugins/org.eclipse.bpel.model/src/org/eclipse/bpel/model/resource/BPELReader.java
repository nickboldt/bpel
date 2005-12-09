/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.bpel.model.resource;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import javax.wsdl.WSDLException;
import javax.wsdl.extensions.ExtensibilityElement;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;

import org.eclipse.bpel.model.Activity;
import org.eclipse.bpel.model.Assign;
import org.eclipse.bpel.model.BPELFactory;
import org.eclipse.bpel.model.BPELPackage;
import org.eclipse.bpel.model.BPELPlugin;
import org.eclipse.bpel.model.Case;
import org.eclipse.bpel.model.Catch;
import org.eclipse.bpel.model.CatchAll;
import org.eclipse.bpel.model.Compensate;
import org.eclipse.bpel.model.CompensationHandler;
import org.eclipse.bpel.model.Condition;
import org.eclipse.bpel.model.Copy;
import org.eclipse.bpel.model.Correlation;
import org.eclipse.bpel.model.CorrelationPattern;
import org.eclipse.bpel.model.CorrelationSet;
import org.eclipse.bpel.model.CorrelationSets;
import org.eclipse.bpel.model.Correlations;
import org.eclipse.bpel.model.Empty;
import org.eclipse.bpel.model.EndpointReferenceRole;
import org.eclipse.bpel.model.EventHandler;
import org.eclipse.bpel.model.Expression;
import org.eclipse.bpel.model.Extension;
import org.eclipse.bpel.model.ExtensionActivity;
import org.eclipse.bpel.model.Extensions;
import org.eclipse.bpel.model.FaultHandler;
import org.eclipse.bpel.model.Flow;
import org.eclipse.bpel.model.From;
import org.eclipse.bpel.model.FromPart;
import org.eclipse.bpel.model.Import;
import org.eclipse.bpel.model.Invoke;
import org.eclipse.bpel.model.Link;
import org.eclipse.bpel.model.Links;
import org.eclipse.bpel.model.OnAlarm;
import org.eclipse.bpel.model.OnEvent;
import org.eclipse.bpel.model.OnMessage;
import org.eclipse.bpel.model.OpaqueActivity;
import org.eclipse.bpel.model.Otherwise;
import org.eclipse.bpel.model.PartnerActivity;
import org.eclipse.bpel.model.PartnerLink;
import org.eclipse.bpel.model.PartnerLinks;
import org.eclipse.bpel.model.Pick;
import org.eclipse.bpel.model.Process;
import org.eclipse.bpel.model.Query;
import org.eclipse.bpel.model.Receive;
import org.eclipse.bpel.model.Reply;
import org.eclipse.bpel.model.Rethrow;
import org.eclipse.bpel.model.Scope;
import org.eclipse.bpel.model.Sequence;
import org.eclipse.bpel.model.ServiceRef;
import org.eclipse.bpel.model.Source;
import org.eclipse.bpel.model.Sources;
import org.eclipse.bpel.model.Switch;
import org.eclipse.bpel.model.Target;
import org.eclipse.bpel.model.Targets;
import org.eclipse.bpel.model.Terminate;
import org.eclipse.bpel.model.Throw;
import org.eclipse.bpel.model.To;
import org.eclipse.bpel.model.ToPart;
import org.eclipse.bpel.model.Variable;
import org.eclipse.bpel.model.Variables;
import org.eclipse.bpel.model.Wait;
import org.eclipse.bpel.model.While;
import org.eclipse.bpel.model.extensions.BPELExtensionDeserializer;
import org.eclipse.bpel.model.extensions.BPELExtensionRegistry;
import org.eclipse.bpel.model.extensions.BPELUnknownExtensionDeserializer;
import org.eclipse.bpel.model.extensions.ServiceReferenceDeserializer;
import org.eclipse.bpel.model.impl.OnEventImpl;
import org.eclipse.bpel.model.impl.OnMessageImpl;
import org.eclipse.bpel.model.impl.PartnerActivityImpl;
import org.eclipse.bpel.model.impl.ToImpl;
import org.eclipse.bpel.model.messageproperties.Property;
import org.eclipse.bpel.model.messageproperties.util.MessagepropertiesConstants;
import org.eclipse.bpel.model.proxy.CorrelationSetProxy;
import org.eclipse.bpel.model.proxy.LinkProxy;
import org.eclipse.bpel.model.proxy.MessageProxy;
import org.eclipse.bpel.model.proxy.PartnerLinkProxy;
import org.eclipse.bpel.model.proxy.PartnerLinkTypeProxy;
import org.eclipse.bpel.model.proxy.PropertyProxy;
import org.eclipse.bpel.model.proxy.RoleProxy;
import org.eclipse.bpel.model.proxy.VariableProxy;
import org.eclipse.bpel.model.proxy.XSDElementDeclarationProxy;
import org.eclipse.bpel.model.proxy.XSDTypeDefinitionProxy;
import org.eclipse.bpel.model.util.BPELUtils;
import org.eclipse.emf.common.util.BasicEList;
import org.eclipse.emf.common.util.WrappedException;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.wst.wsdl.ExtensibleElement;
import org.eclipse.wst.wsdl.Message;
import org.eclipse.wst.wsdl.PortType;
import org.eclipse.xsd.XSDElementDeclaration;
import org.eclipse.xsd.XSDTypeDefinition;
import org.eclipse.xsd.util.XSDConstants;
import org.w3c.dom.Attr;
import org.w3c.dom.CDATASection;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * BPELReader is invoked from BPELResourceImpl to parse the BPEL file and
 * create a Process object.
 */
public class BPELReader {

	// The process we are reading
	private Process process = null;
	// The resource we are reading from
	private BPELResource resource = null;
	// The document builder controls various DOM characteristics
	private DocumentBuilder docBuilder = null;
	// Registry for extensibility element serializers and deserializers
	private BPELExtensionRegistry extensionRegistry = BPELExtensionRegistry.getInstance();
	// The WS-BPEL Specification says how to resolve variables, taking into
	// account scopes, etc. Technically, no one should override this behaviour,
	// but replacing this field with another implementation could allow
	// you to optimize the search or provide different behaviour.
	public static VariableResolver VARIABLE_RESOLVER = new BPELVariableResolver();
	
	/**
	 * A simple NodeList that is an ArrayList
	 */
	class BPELNodeList extends ArrayList implements NodeList {
		public Node item(int index) {
			return (Node)get(index);
		}

		public int getLength() {
			return this.size();
		}
	}
	
	/**
	 * Construct a new BPELReader using the given DocumentBuilder to determine
	 * how the DOM tree is constructed.
	 * 
	 * @param builder  the document builder to use when parsing the file
	 * @throws IOException if no document builder is specified
	 */
	public BPELReader(DocumentBuilder builder) throws IOException {
		if (builder == null) {
			throw new IOException(BPELPlugin.INSTANCE.getString("%BPELReader.missing_doc_builder"));
		}
		this.docBuilder = builder;
	}

	/**
	 * Read from the given input stream into the given resource.
	 * 
	 * @param resource  the EMF resource to construct
	 * @param inputStream  the input stream to read the BPEL from
	 * @throws IOException if an error occurs during reading
	 */
	public void read(BPELResource resource, InputStream inputStream) throws IOException {
		try {
			Document doc = docBuilder.parse(inputStream);
			// After the document has successfully parsed, it's okay
			// to assign the resource.
			this.resource = resource;
			// Pass 1 and 2 are inside the try so they don't occur if
			// an error happens during parsing.
			// In pass 1 we parse and create the structural elements and attributes. 
			pass1(doc);
			// In pass 2, we run any postLoadRunnables which need to happen after
			// pass 1 (for example, establishing object links to varables).
			pass2();
		} catch (SAXParseException exc) {
			// TODO: Error handling
			exc.printStackTrace();
		} catch (SAXException se) {
			// TODO: Error handling
			se.printStackTrace();
		} catch (EOFException exc) {
			// Ignore end of file exception
		} catch (IOException ioe) {
			// TODO: Error handling
			ioe.printStackTrace();
		}
	}

	/**
	 * In pass 1, we parse and create the structural elements and attributes,
	 * and add the process to the EMF resource's contents
	 * @param document  the DOM document to parse
	 */
	protected void pass1(Document document) {
		resource.getContents().add(xml2Resource(document));		
	}
	
	/**
	 * In pass 2, we run any post load runnables which were queued during pass 1.
	 */
	protected void pass2() {
		if (process != null && process.getPostLoadRunnables() != null) {
			for (Iterator it = process.getPostLoadRunnables().iterator(); it.hasNext();) {
				Runnable runnable = (Runnable)it.next();
				runnable.run();
			}
			process.getPostLoadRunnables().clear();
		}	
	}
	
	/**
     * Returns a list of child nodes of <code>parentElement</code> that are
     * {@link Element}s.
     * Returns an empty list if no elements are found.
     * 
	 * @param parentElement  the element to find the children of
	 * @return a node list of the children of parentElement
	 */
	protected BPELNodeList getChildElements(Element parentElement) {
		BPELNodeList list = new BPELNodeList();
		NodeList children = parentElement.getChildNodes();		
		for (int i=0; i < children.getLength(); i++) {
			if (children.item(i).getNodeType() == Node.ELEMENT_NODE)
				list.add(children.item(i));
		}
		return list;
	}

    /**
     * Returns a list of child nodes of <code>parentElement</code> that are
     * {@link Element}s with a BPEL namespace that have the given <code>localName</code>.
     * Returns an empty list if no matching elements are found.
     * 
	 * @param parentElement  the element to find the children of
	 * @param localName  the localName to match against
	 * @return a node list of the matching children of parentElement
     */
	protected BPELNodeList getBPELChildElementsByLocalName(Element parentElement, String localName) {
		BPELNodeList list = new BPELNodeList();
		NodeList children = parentElement.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node node = children.item(i);
			if (localName.equals(node.getLocalName()) && BPELUtils.isBPELElement(node)) {
                list.add(node);
			}
		}
		return list;
	}

    /**
     * Returns the first child node of <code>parentElement</code> that is an {@link Element}
     * with a BPEL namespace and the given <code>localName</code>, or <code>null</code>
     * if a matching element is not found. 
     * 
	 * @param parentElement  the element to find the children of
	 * @param localName  the localName to match against
	 * @return the first matching element, or null if no element was found
      */
	protected Element getBPELChildElementByLocalName(Element parentElement, String localName) {
		NodeList children = parentElement.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node node = children.item(i);
			if (localName.equals(node.getLocalName()) && BPELUtils.isBPELElement(node)) {
                return (Element) node;
            }
		}
		return null;
	}

    /**
	 * Walk from the given element up through its parents, looking for any
	 * xmlns definitions. Collect them all in a map (mapping the prefix to
	 * the namespace value) and return the map.
	 * 
	 * @param element  the element to get the xmlns definitions for
	 * @return a map of visible xmlns definitions
	 */
	protected Map getAllNamespacesForElement(Element element) {
		Map nsMap = new HashMap();		
		Node tempNode = element;        
		while (tempNode != null && tempNode.getNodeType() == Node.ELEMENT_NODE) {
			NamedNodeMap attrs = ((Element)tempNode).getAttributes();
			for (int i = 0; i < attrs.getLength(); i++) {
				Attr attr = (Attr)attrs.item(i);
				// XML namespace attributes use the reserved namespace "http://www.w3.org/2000/xmlns/".
				if (XSDConstants.XMLNS_URI_2000.equalsIgnoreCase(attr.getNamespaceURI())) {
					final String key = BPELUtils.getNSPrefixMapKey(attr.getLocalName());
					if (!nsMap.containsKey(key)) {
						nsMap.put(key, attr.getValue());
					}
				}
			}
			tempNode = tempNode.getParentNode();
		}
		return nsMap;
	}
	
	/**
	 * For all attributes of the given element, ensure that their namespace
	 * prefixes are in the resource's prefix-to-namespace-map.
	 * 
	 * @param eObject
	 * @param element
	 */
	protected void saveNamespacePrefix(EObject eObject, Element element) {
		Map nsMap = null; // lazy init since it may require a new map
		NamedNodeMap attrs = element.getAttributes();
		for (int i=0; i < attrs.getLength(); i++) {
			Attr attr = (Attr) attrs.item(i);        
			// XML namespace attributes use the reserved namespace "http://www.w3.org/2000/xmlns/". 
			if (XSDConstants.XMLNS_URI_2000.equals(attr.getNamespaceURI())) {
				if (nsMap == null) {
					nsMap = resource.getPrefixToNamespaceMap(eObject);
				}
				nsMap.put(BPELUtils.getNSPrefixMapKey(attr.getLocalName()), attr.getValue());
			}
		}
	}

	/**
	 * Given a DOM Element, find the child element which is a BPEL activity
	 * (of some type), parse it, and return the Activity.
	 * 
	 * @param element  the element in which to find an activity
	 * @return the activity, or null if no activity could be found
	 */
	protected Activity getChildActivity(Element element) {
		NodeList activityElements = element.getChildNodes();
		for (int i = 0; i < activityElements.getLength(); i++) {
			if (activityElements.item(i).getNodeType() != Node.ELEMENT_NODE) {
				continue;
			}
			           	   	         	
			Element activityElement = (Element)activityElements.item(i);
			Activity activity = xml2Activity(activityElement);
							
			if (activity != null) {
				return activity;	
			}
		}
		return null;   	
	}

 
	/**
	 * Sets a PartnerLink element for a given EObject. The given activity element
	 * must contain an attribute named "partnerLink".
	 * 
	 * @param activityElement  the DOM element of the activity
	 * @param eObject  the EObject in which to set the partner link
	 */
	protected void setPartnerLink(Element activityElement, final EObject eObject, final EReference reference) {
		if (!activityElement.hasAttribute("partnerLink")) {
			return;
		}

		final String partnerLinkName = activityElement.getAttribute("partnerLink");
		// We must do this as a post load runnable because the partner link might not
		// exist yet.
		process.getPostLoadRunnables().add(new Runnable() {
			public void run() {	
				PartnerLink targetPartnerLink = BPELUtils.getPartnerLink(eObject, partnerLinkName);
				if (targetPartnerLink == null) {
					targetPartnerLink = new PartnerLinkProxy(resource.getURI(), partnerLinkName);
				}
				eObject.eSet(reference, targetPartnerLink);				
			}
		});		
	}

	/**
	 * Sets a Variable element for a given EObject. The given activity element
	 * must contain an attribute with the given name
	 * 
	 * @param activityElement  the DOM element of the activity
	 * @param eObject  the EObject in which to set the variable
	 * @param variableAttrName  the name of the attribute containing the variable name
	 * @param reference  the EReference which is the variable pointer in EObject 
	 */
	protected void setVariable(Element activityElement, final EObject eObject, String variableNameAttr, final EReference reference) {
		if (!activityElement.hasAttribute(variableNameAttr)) {
			return;
		}

		final String variableName = activityElement.getAttribute(variableNameAttr);
		// We must do this as a post load runnable because the variable might not
		// exist yet.
		process.getPostLoadRunnables().add(new Runnable() {
			public void run() {				
				Variable targetVariable = getVariable(eObject, variableName);
				if (targetVariable == null) {
					targetVariable = new VariableProxy(resource.getURI(), variableName);
				}		
				eObject.eSet(reference, targetVariable);				
			}
		});
  	} 	

	/**
	 * Find a Property name in element (in the named attribute) and set it
	 * into the given EObject. If EObject is a CorrelationSet, add the property
	 * to the list of properties. If it is a To, set the property.
	 * 
	 * @param element  the DOM element containing the property name
	 * @param eObject  the EObject in which to set the property
	 * @param propertyName  the name of the attribute containing the property name
	 */
	protected void setProperties(Element element, EObject eObject, String propertyName) {
		String propertyAttribute = element.getAttribute(propertyName);
		
		StringTokenizer st = new StringTokenizer(propertyAttribute);

		while (st.hasMoreTokens()) {
			QName qName = BPELUtils.createQName(element, st.nextToken());
			Property property = new PropertyProxy(resource.getURI(), qName);
			if (eObject instanceof CorrelationSet) {
				((CorrelationSet)eObject).getProperties().add(property);
			} else if (eObject instanceof To) {
				((To)eObject).setProperty(property);
			}
		}
	}

	/**
	 * Sets a CompensationHandler element for a given eObject.
	 */
	protected void setCompensationHandler(Element element, EObject eObject) {
       Element compensationHandlerElement = getBPELChildElementByLocalName(element, "compensationHandler");
                 
		if (compensationHandlerElement != null) {
			CompensationHandler compensationHandler = xml2CompensationHandler(compensationHandlerElement);
			xml2ExtensibleElement(compensationHandler, compensationHandlerElement); 

			if (eObject instanceof Invoke)	
				((Invoke)eObject).setCompensationHandler(compensationHandler);
			else if (eObject instanceof Scope)		
				((Scope)eObject).setCompensationHandler(compensationHandler);
        }  
	}

	/**
	 * Sets a FaultHandler element for a given extensibleElement.
	 */
	protected void setFaultHandler(Element element, ExtensibleElement extensibleElement) {
		NodeList faultHandlerElements = getBPELChildElementsByLocalName(element, "faultHandlers");
		
		if (faultHandlerElements != null && faultHandlerElements.getLength() > 0) {
			FaultHandler faultHandler =	xml2FaultHandler((Element)faultHandlerElements.item(0)); 
			
			if (extensibleElement instanceof Process) {
				((Process)extensibleElement).setFaultHandlers(faultHandler);
			} else if (extensibleElement instanceof Invoke) {
				((Invoke)extensibleElement).setFaultHandler(faultHandler);
			}
		}
	}
	
	/**
	 * Sets a EventHandler element for a given extensibleElement.
	 */
	protected void setEventHandler(Element element, ExtensibleElement extensibleElement) {
		NodeList eventHandlerElements = getBPELChildElementsByLocalName(element, "eventHandlers");
                 
		if (eventHandlerElements != null && eventHandlerElements.getLength() > 0) {
			EventHandler eventHandler =	xml2EventHandler((Element)eventHandlerElements.item(0)); 

			if (extensibleElement instanceof Process) ((Process)extensibleElement).setEventHandlers(eventHandler);
				else if (extensibleElement instanceof Scope) ((Scope)extensibleElement).setEventHandlers(eventHandler);
		}
	}	


	/**
	 * Sets the standard attributes (name, joinCondition, and suppressJoinFailure).
	 */
	protected void setStandardAttributes(Element activityElement, Activity activity) {

		// Set name
		Attr name = activityElement.getAttributeNode("name");
		
		if (name != null && name.getSpecified())		
			activity.setName(name.getValue());

		// Set suppress join failure
		Attr suppressJoinFailure = activityElement.getAttributeNode("suppressJoinFailure");
		
		if (suppressJoinFailure != null && suppressJoinFailure.getSpecified())		
			activity.setSuppressJoinFailure(new Boolean(suppressJoinFailure.getValue().equals("yes")));
	}


	/**
	 * Sets name, portType, operation, partner, variable and correlation for a given PartnerActivity object.
	 */
	protected void setOperationParms(final Element activityElement,
									 final PartnerActivity activity,
									 EReference variableReference,
									 EReference inputVariableReference,
									 EReference outputVariableReference,
									 EReference partnerReference) {
		// Set partnerLink
		setPartnerLink(activityElement, activity, partnerReference);

		// Set portType
        PortType portType = null;
        if (activityElement.hasAttribute("portType")) {
            portType = BPELUtils.getPortType(resource.getURI(), activityElement, "portType");
            activity.setPortType(portType);
        }

		// Set operation
		if (activityElement.hasAttribute("operation")) {
            if (portType != null) {
				activity.setOperation(BPELUtils.getOperation(resource.getURI(), portType, activityElement, "operation"));
			} else {
                ((PartnerActivityImpl) activity).setOperationName(activityElement.getAttribute("operation"));
            }
		}
		
		// Set variable
		if (variableReference != null) {
			setVariable(activityElement, activity, "variable", variableReference);
		}
		if (inputVariableReference != null) {
			setVariable(activityElement, activity, "inputVariable", inputVariableReference);
		}
		if (outputVariableReference != null) {
			setVariable(activityElement, activity, "outputVariable", outputVariableReference);
		}
		
		// Set correlations
		Element correlationsElement = getBPELChildElementByLocalName(activityElement, "correlations");
		if (correlationsElement != null) {
			Correlations correlations = xml2Correlations(correlationsElement);
			activity.setCorrelations(correlations);
		}
	}

	/**
	 * Sets name, portType, operation, partner, variable and correlation for a given PartnerActivity object.
	 */
	protected void setOperationParmsOnMessage(final Element activityElement, final OnMessage onMessage) {
		// Set partnerLink
		setPartnerLink(activityElement, onMessage, BPELPackage.eINSTANCE.getOnMessage_PartnerLink());

        // Set portType
        PortType portType = null;
        if (activityElement.hasAttribute("portType")) {
            portType = BPELUtils.getPortType(resource.getURI(), activityElement, "portType");
            onMessage.setPortType(portType);
        }
        
        // Set operation
        if (activityElement.hasAttribute("operation")) {
            if (portType != null) {
                onMessage.setOperation(BPELUtils.getOperation(resource.getURI(), portType, activityElement, "operation"));
            } else {
                // If portType is not specified it will be resolved lazily and so will the operation.
                // Save the deserialized name so the operation can be later resolved.
                ((OnMessageImpl) onMessage).setOperationName(activityElement.getAttribute("operation"));
            }
        }

		// Set variable
		setVariable(activityElement, onMessage, "variable", BPELPackage.eINSTANCE.getOnMessage_Variable());

		// Set correlations
		Element correlationsElement = getBPELChildElementByLocalName(activityElement, "correlations");
		if (correlationsElement != null) {
			Correlations correlations = xml2Correlations(correlationsElement);
			onMessage.setCorrelations(correlations);
		}
	}

	/**
	 * Sets name, portType, operation, partner, variable, messageType and correlation for a given PartnerActivity object.
	 */
	protected void setOperationParmsOnEvent(final Element activityElement, final OnEvent onEvent) {
		// Set partnerLink
		setPartnerLink(activityElement, onEvent, BPELPackage.eINSTANCE.getOnEvent_PartnerLink());

        // Set portType
        PortType portType = null;
        if (activityElement.hasAttribute("portType")) {
            portType = BPELUtils.getPortType(resource.getURI(), activityElement, "portType");
            onEvent.setPortType(portType);
        }

        // Set operation
        if (activityElement.hasAttribute("operation")) {
            if (portType != null) {
                onEvent.setOperation(BPELUtils.getOperation(resource.getURI(), portType, activityElement, "operation"));
            } else {
                ((OnEventImpl) onEvent).setOperationName(activityElement.getAttribute("operation"));
            }
        }

		// Set variable
		if (activityElement.hasAttribute("variable")) {
			Variable variable = BPELFactory.eINSTANCE.createVariable();		
	
			// Set name
			String name = activityElement.getAttribute("variable");
			variable.setName(name);
			onEvent.setVariable(variable);
			// Don't set the message type of the variable, this will happen
			// in the next step.
		}
		
		// Set message type
		if (activityElement.hasAttribute("messageType")) {
			QName qName = BPELUtils.createAttributeValue(activityElement, "messageType");
			Message messageType = new MessageProxy(resource.getURI(), qName);
			onEvent.setMessageType(messageType);
		}

		// Set correlations
		Element correlationsElement = getBPELChildElementByLocalName(activityElement, "correlations");
		if (correlationsElement != null) {
			Correlations correlations = xml2Correlations(correlationsElement);
			onEvent.setCorrelations(correlations);
		}
	}

	/**
	 * Converts an XML document to a BPEL Resource object.
	 */
	protected Process xml2Resource(Document document) {
		Element processElement = (document != null)? document.getDocumentElement(): null;
		Process process = xml2Process(processElement);
		return process;	
	}


	/**
	 * Converts an XML process to a BPEL Process object.
	 */
	protected Process xml2Process(Element processElement) {
		if (!processElement.getLocalName().equals("process"))
			return null;
			
		process = BPELFactory.eINSTANCE.createProcess();
		
		// Save all the references to external namespaces		
		saveNamespacePrefix(process, processElement);
		
		// Handle Process element
		if (processElement.hasAttribute("name")) 
			process.setName(processElement.getAttribute("name"));
		
		if (processElement.hasAttribute("targetNamespace"))	
			process.setTargetNamespace(processElement.getAttribute("targetNamespace"));
		
		if (processElement.hasAttribute("suppressJoinFailure"))
			process.setSuppressJoinFailure(new Boolean(processElement.getAttribute("suppressJoinFailure").equals("yes")));
		
		if (processElement.hasAttribute("variableAccessSerializable"))
			process.setVariableAccessSerializable(new Boolean(processElement.getAttribute("variableAccessSerializable").equals("yes")));

		if (processElement.hasAttribute("queryLanguage"))
			process.setQueryLanguage(processElement.getAttribute("queryLanguage"));

		if (processElement.hasAttribute("expressionLanguage"))
			process.setExpressionLanguage(processElement.getAttribute("expressionLanguage"));
			
		// Handle Import Elements
		BPELNodeList importElements = getBPELChildElementsByLocalName(processElement, "import");
		for (int i=0; i < importElements.getLength(); i++) {
			Element importElement = (Element)importElements.item(i);
			Import imp = xml2Import(importElement);
			process.getImports().add(imp);
		}
		
		// Handle PartnerLinks Element
		Element partnerLinksElement = getBPELChildElementByLocalName(processElement, "partnerLinks");
		if (partnerLinksElement != null)
			process.setPartnerLinks(xml2PartnerLinks(partnerLinksElement));
			
		// Handle Variables Element
		Element variablesElement = getBPELChildElementByLocalName(processElement, "variables");
		if (variablesElement != null)
			process.setVariables(xml2Variables(variablesElement));
			
		// Handle CorrelationSets Element
		Element correlationSetsElement = getBPELChildElementByLocalName(processElement, "correlationSets");
		if (correlationSetsElement != null)
			process.setCorrelationSets(xml2CorrelationSets(correlationSetsElement));
			 
		// Handle Extensions Element
		Element extensionsElement = getBPELChildElementByLocalName(processElement, "extensions");
		if (extensionsElement != null)
			process.setExtensions(xml2Extensions(extensionsElement));

		// Handle FaultHandler element
		setFaultHandler(processElement, process);
		
		// Handle CompensationHandler element
		// In BPEL 2.0, there is no compensation handler on process
		//setCompensationHandler(processElement, process);
		
		// Handle EventHandler element
		setEventHandler(processElement, process);
		
 		// Handle Activity elements
        Activity activity = xml2Activity(processElement); 
        process.setActivity(activity); 

		xml2ExtensibleElement(process,processElement);
		
		return process;
	}
	
	/**
	 * Converts an XML partnerLinks
	 */
	protected PartnerLinks xml2PartnerLinks(Element partnerLinksElement) {
		if (!partnerLinksElement.getLocalName().equals("partnerLinks"))
			return null;
			
		PartnerLinks partnerLinks = BPELFactory.eINSTANCE.createPartnerLinks();

		// Save all the references to external namespaces		
		saveNamespacePrefix(partnerLinks, partnerLinksElement);
		
		BPELNodeList partnerLinkElements = getBPELChildElementsByLocalName(partnerLinksElement, "partnerLink");
		for (int i=0; i < partnerLinkElements.getLength(); i++) {
			Element partnerLinkElement = (Element)partnerLinkElements.item(i);
			PartnerLink partnerLink = xml2PartnerLink(partnerLinkElement);
			partnerLinks.getChildren().add(partnerLink);
		}
		
		xml2ExtensibleElement(partnerLinks, partnerLinksElement);
	
		return partnerLinks;
	}

	protected Variables xml2Variables(Element variablesElement) {
		if (!variablesElement.getLocalName().equals("variables"))
			return null;
			
		Variables variables = BPELFactory.eINSTANCE.createVariables();
		
		// Save all the references to external namespaces		
		saveNamespacePrefix(variables, variablesElement);		
		
		BPELNodeList variableElements = getBPELChildElementsByLocalName(variablesElement, "variable");
		for (int i=0; i < variableElements.getLength(); i++) {
			Element variableElement = (Element)variableElements.item(i);
			if (BPELUtils.isBPELNamespace(variableElement.getNamespaceURI())) {
				Variable variable = xml2Variable(variableElement);
				variables.getChildren().add(variable);
			}
		}
		
		xml2ExtensibleElement(variables, variablesElement);
		
		// Move variables that are extensibility elements to the list of children
		// JM: What is this supposed to accomplish?
		List toBeMoved = new BasicEList();
		for (Iterator iter = variables.getExtensibilityElements().iterator(); iter.hasNext();) {
			ExtensibilityElement element = (ExtensibilityElement) iter.next();
			if(element instanceof Variable)
				toBeMoved.add(element);
		}
		
		List children = variables.getChildren();
		List extensibility = variables.getExtensibilityElements();
		for (Iterator iter = toBeMoved.iterator(); iter.hasNext();) {
			Variable element = (Variable) iter.next();
			extensibility.remove(element);
			children.add(element);
		}
		
		return variables;
	}
	
	protected CorrelationSets xml2CorrelationSets(Element correlationSetsElement) {
		if (!correlationSetsElement.getLocalName().equals("correlationSets"))
			return null;
			
		CorrelationSets correlationSets = BPELFactory.eINSTANCE.createCorrelationSets();
		
		// Save all the references to external namespaces		
		saveNamespacePrefix(correlationSets, correlationSetsElement);		
		
		BPELNodeList correlationSetElements = getBPELChildElementsByLocalName(correlationSetsElement, "correlationSet");
		for (int i=0; i < correlationSetElements.getLength(); i++) {
			Element correlationSetElement = (Element)correlationSetElements.item(i);
			CorrelationSet correlationSet = xml2CorrelationSet(correlationSetElement);
			correlationSets.getChildren().add(correlationSet);
		}
		
		xml2ExtensibleElement(correlationSets, correlationSetsElement);
		
		return correlationSets;
	}

	protected Extensions xml2Extensions(Element extensionsElement) {
		if (!extensionsElement.getLocalName().equals("extensions"))
			return null;
			
		Extensions extensions = BPELFactory.eINSTANCE.createExtensions();
		
		// Save all the references to external namespaces		
		saveNamespacePrefix(extensions, extensionsElement);		
		
		BPELNodeList extensionElements = getBPELChildElementsByLocalName(extensionsElement, "extension");
		for (int i=0; i < extensionElements.getLength(); i++) {
			Element extensionElement = (Element)extensionElements.item(i);
			Extension extension = xml2Extension(extensionElement);
			extensions.getChildren().add(extension);
		}
		
		xml2ExtensibleElement(extensions, extensionsElement);
		
		return extensions;
	}

	/**
	 * Converts an XML compensationHandler element to a BPEL CompensationHandler object.
	 */
	protected CompensationHandler xml2CompensationHandler(Element activityElement) {
		CompensationHandler compensationHandler = BPELFactory.eINSTANCE.createCompensationHandler();
		
		// Save all the references to external namespaces		
		saveNamespacePrefix(compensationHandler, activityElement);
		
		compensationHandler.setActivity(getChildActivity(activityElement));
		
		return compensationHandler;
	}


	/**
	 * Converts an XML correlationSet element to a BPEL CorrelationSet object.
	 */
	protected CorrelationSet xml2CorrelationSet(Element correlationSetElement) {
		CorrelationSet correlationSet = BPELFactory.eINSTANCE.createCorrelationSet();		
		
		// Save all the references to external namespaces		
		saveNamespacePrefix(correlationSet, correlationSetElement);		
		
		if (correlationSetElement == null) return correlationSet;
		
		// Set name
		Attr name = correlationSetElement.getAttributeNode("name");

		if (name != null && name.getSpecified())		
			correlationSet.setName(name.getValue());

		setProperties(correlationSetElement, correlationSet, "properties");
		
		xml2ExtensibleElement(correlationSet, correlationSetElement);

		return correlationSet;
	}

	/**
	 * Converts an XML extension element to a BPEL Extension object.
	 */
	protected Extension xml2Extension(Element extensionElement) {
		Extension extension = BPELFactory.eINSTANCE.createExtension();
		
		// Save all the references to external namespaces		
		saveNamespacePrefix(extension, extensionElement);		
		
		if (extensionElement == null) return extension;
		
		// Set namespace
		if (extensionElement.hasAttribute("namespace"))	
			extension.setNamespace(extensionElement.getAttribute("namespace"));
		
		// Set mustUnderstand
		if (extensionElement.hasAttribute("mustUnderstand"))
			extension.setMustUnderstand(new Boolean(extensionElement.getAttribute("mustUnderstand").equals("yes")));
		
		xml2ExtensibleElement(extension, extensionElement);

		return extension;
	}

	/**
	 * Converts an XML partnerLink element to a BPEL PartnerLink object.
	 */
  	protected PartnerLink xml2PartnerLink(Element partnerLinkElement) {
		if (!partnerLinkElement.getLocalName().equals("partnerLink"))
			return null;
			 
		PartnerLink partnerLink = BPELFactory.eINSTANCE.createPartnerLink();		
		// Save all the references to external namespaces		
		saveNamespacePrefix(partnerLink, partnerLinkElement);
		
		// Set name
		if (partnerLinkElement.hasAttribute("name"))
			partnerLink.setName(partnerLinkElement.getAttribute("name"));
			
		Attr partnerLinkTypeName = partnerLinkElement.getAttributeNode("partnerLinkType");
		if (partnerLinkTypeName != null && partnerLinkTypeName.getSpecified()) {
			QName sltQName = BPELUtils.createAttributeValue(partnerLinkElement, "partnerLinkType");
			
			PartnerLinkTypeProxy slt = new PartnerLinkTypeProxy(resource.getURI(), sltQName);
			partnerLink.setPartnerLinkType(slt);
			
			if(slt != null) {
				partnerLink.setPartnerLinkType(slt);
				
				if (partnerLinkElement.hasAttribute("myRole")) {
					RoleProxy role = new RoleProxy(resource, slt, partnerLinkElement.getAttribute("myRole"));
					partnerLink.setMyRole(role);
				}
				if (partnerLinkElement.hasAttribute("partnerRole")) {
					RoleProxy role = new RoleProxy(resource, slt, partnerLinkElement.getAttribute("partnerRole"));
					partnerLink.setPartnerRole(role);
				}
			}
		}

		xml2ExtensibleElement(partnerLink,partnerLinkElement);

        return partnerLink;
     }


	/**
	 * Converts an XML variable element to a BPEL Variable object.
	 */
	protected Variable xml2Variable(Element variableElement) {
		if (!variableElement.getLocalName().equals("variable"))
			return null;
			 
		Variable variable = BPELFactory.eINSTANCE.createVariable();		
		// Save all the references to external namespaces		
		saveNamespacePrefix(variable, variableElement);

		// Set name
		if (variableElement.hasAttribute("name")) {
			String name = variableElement.getAttribute("name");
			variable.setName(name);
		}
		
		if (variableElement.hasAttribute("messageType")) {
			QName qName = BPELUtils.createAttributeValue(variableElement,"messageType");
			Message messageType = new MessageProxy(resource.getURI(), qName);
			variable.setMessageType(messageType);
		}

		// Set xsd type
		if (variableElement.hasAttribute("type")) {
			QName qName = BPELUtils.createAttributeValue(variableElement, "type");
			XSDTypeDefinition type = new XSDTypeDefinitionProxy(resource.getURI(), qName);
			variable.setType(type);						
		}
		
		// Set xsd element
		if (variableElement.hasAttribute("element")) {
			QName qName = BPELUtils.createAttributeValue(variableElement, "element");
			XSDElementDeclaration element = new XSDElementDeclarationProxy(resource.getURI(), qName);
			variable.setXSDElement(element);			
		}

		xml2ExtensibleElement(variable,variableElement);
		
        return variable;
     }

	/**
	 * Converts an XML faultHandler element to a BPEL FaultHandler object.
	 */
 	protected FaultHandler xml2FaultHandler(Element faultHandlerElement) { 
 		if (!(faultHandlerElement.getLocalName().equals("faultHandlers") ||
 			faultHandlerElement.getLocalName().equals("invoke")))
 			return null;
 			
		FaultHandler faultHandler = BPELFactory.eINSTANCE.createFaultHandler();
		
		// Save all the references to external namespaces		
		saveNamespacePrefix(faultHandler, faultHandlerElement);
				
		BPELNodeList catchElements = getBPELChildElementsByLocalName(faultHandlerElement, "catch");
		for (int i=0; i < catchElements.getLength(); i++) { 
			Element catchElement = (Element)catchElements.item(i);				              
			Catch _catch = xml2Catch(catchElement); 
			faultHandler.getCatch().add(_catch); 			
		}

		Element catchAllElement = getBPELChildElementByLocalName(faultHandlerElement, "catchAll");
		if (catchAllElement != null) {
			CatchAll catchAll = xml2CatchAll(catchAllElement);
			faultHandler.setCatchAll(catchAll);
		}
		
		// Only do this for an element named faultHandlers. If the element is named
		// invoke, then there really is no fault handler, only a series of catches.
		if (faultHandlerElement.getLocalName().equals("faultHandlers")) {
			xml2ExtensibleElement(faultHandler, faultHandlerElement);
		}
				
		return faultHandler;		
 	}

	/**
	 * Converts an XML catchAll element to a BPEL CatchAll object.
	 */
	protected CatchAll xml2CatchAll(Element catchAllElement) {
		if (!catchAllElement.getLocalName().equals("catchAll"))
			return null;
			
		CatchAll catchAll = BPELFactory.eINSTANCE.createCatchAll();
		
		// Save all the references to external namespaces		
		saveNamespacePrefix(catchAll, catchAllElement);		
		
		BPELNodeList elements = getChildElements(catchAllElement);
		for (int i=0; i < elements.getLength(); i++) {
			Element element = (Element)elements.item(i);
			Activity activity = xml2Activity(element);
			if (activity != null) {
				catchAll.setActivity(activity);
				break;
			}
		}
		
		xml2ExtensibleElement(catchAll, catchAllElement);
		
		return catchAll;
	}

	/**
	 * Converts an XML catch element to a BPEL Catch object.
	 */
	protected Catch xml2Catch(Element catchElement) {
		Catch _catch = BPELFactory.eINSTANCE.createCatch();
		
		// Save all the references to external namespaces		
		saveNamespacePrefix(_catch, catchElement);
		
		if (catchElement == null) return _catch;
		
		if (catchElement.hasAttribute("faultName")) {
			QName qName = BPELUtils.createAttributeValue(catchElement, "faultName");	
			_catch.setFaultName(qName);
		}

		if (catchElement.hasAttribute("faultVariable")) {
			// Set fault variable
			Variable variable = BPELFactory.eINSTANCE.createVariable();		
			variable.setName(catchElement.getAttribute("faultVariable"));
			_catch.setFaultVariable(variable);					
		}		
		
		if (catchElement.hasAttribute("faultMessageType")) {
			QName qName = BPELUtils.createAttributeValue(catchElement,"faultMessageType");
			Message messageType = new MessageProxy(resource.getURI(), qName);
			_catch.setFaultMessageType(messageType);
		}

		if (catchElement.hasAttribute("faultElement")) {
			QName qName = BPELUtils.createAttributeValue(catchElement,"faultElement");
			XSDElementDeclaration element = new XSDElementDeclarationProxy(resource.getURI(), qName);
			_catch.setFaultElement(element);
		}

		// Set Activities		
		NodeList catchElements = catchElement.getChildNodes();
        
        Element activityElement = null;

		if (catchElements != null && catchElements.getLength() > 0) {
          
           for (int i = 0; i < catchElements.getLength(); i++) {
           	   if (catchElements.item(i).getNodeType() != Node.ELEMENT_NODE)
           	   	  continue;
           	
               activityElement = (Element)catchElements.item(i); 
               Activity activity = xml2Activity(activityElement);
               if (activity != null) 
               	_catch.setActivity(activity); 
           }
        }		

		xml2ExtensibleElement(_catch, catchElement);
		return _catch;
	}

    /**
	 * Converts an XML activity element to a BPEL Activity object.
	 */
     protected Activity xml2Activity(Element activityElement) {
		Activity activity = null;
		boolean checkExtensibility = true;

        if (!BPELUtils.isBPELElement(activityElement))
            return null;
        
		String localName = activityElement.getLocalName();        
        if (localName.equals("process")){ 
			activity = getChildActivity(activityElement);
			checkExtensibility = false;
		} else if (localName.equals("receive")) {
       		activity = xml2Receive(activityElement);
     	} else if (localName.equals("reply")) {
      		activity = xml2Reply(activityElement);
     	} else if (localName.equals("invoke")) {
      		activity = xml2Invoke(activityElement);
     	} else if (localName.equals("assign")) {
      		activity = xml2Assign(activityElement);
     	} else if (localName.equals("throw")) {
      		activity = xml2Throw(activityElement);
     	} else if (localName.equals("terminate")) {
      		activity = xml2Terminate(activityElement);
     	} else if (localName.equals("wait")) {
      		activity = xml2Wait(activityElement);
     	} else if (localName.equals("empty")) {
      		activity = xml2Empty(activityElement);
     	} else if (localName.equals("sequence")) {
      		activity = xml2Sequence(activityElement);
     	} else if (localName.equals("switch")) {
     		activity = xml2Switch(activityElement);
     	} else if (localName.equals("while")) {
     		activity = xml2While(activityElement);
     	} else if (localName.equals("pick")) {
     		activity = xml2Pick(activityElement);
     	} else if (localName.equals("flow")) {
     		activity = xml2Flow(activityElement);
     	} else if (localName.equals("scope")) {
     		activity = xml2Scope(activityElement);
     	} else if (localName.equals("compensate")) {
     		activity = xml2Compensate(activityElement);
     	} else if (localName.equals("rethrow")) {
     		activity = xml2Rethrow(activityElement);
     	} else if (localName.equals("extensionActivity")) {
     		activity = xml2ExtensionActivity(activityElement);
     	} else if (localName.equals("opaqueActivity")) {
     		activity = xml2OpaqueActivity(activityElement);
     	} else {
     		return null;
     	}
     	  	
		// Handle targets
		Element targetsElement = getBPELChildElementByLocalName(activityElement, "targets");
		if (targetsElement != null) {
			activity.setTargets(xml2Targets(targetsElement));
		}
				
		// Handle old targets for backwards compatibility
		// TODO: do join conditions properly
		NodeList targetElements = getBPELChildElementsByLocalName(activityElement, "target");
		for (int i = 0; i < targetElements.getLength(); i++) {
			Element targetElement = (Element)targetElements.item(i);
 			// Avoid parent elements from processing this link again.
			if (!((Element)targetElement.getParentNode()).getLocalName().equals(localName))
				break;
				
			Target target = xml2Target(targetElement);
			target.setActivity(activity);          
        }
        
		// Handle sources
		Element sourcesElement = getBPELChildElementByLocalName(activityElement, "sources");
		if (sourcesElement != null) {
			activity.setSources(xml2Sources(sourcesElement));
		}
		
		// Handle old sources for backwards compatibility
		NodeList sourceElements = getBPELChildElementsByLocalName(activityElement, "source");
		for (int i = 0; i < sourceElements.getLength(); i++) {
			Element sourceElement = (Element)sourceElements.item(i);
			// Avoid parent elements from processing this link again.
			if (!((Element)sourceElement.getParentNode()).getLocalName().equals(localName))
           		break;
           		
           	Source source = xml2Source(sourceElement);
           	source.setActivity(activity);
        }

		if (checkExtensibility) {
			xml2ExtensibleElement(activity, activityElement);
			// Save all the references to external namespaces		
			saveNamespacePrefix(activity, activityElement);
		}			
			
		return activity;
     }

     protected Targets xml2Targets(Element targetsElement) {
		Targets targets = BPELFactory.eINSTANCE.createTargets();
		NodeList targetElements = getBPELChildElementsByLocalName(targetsElement, "target");
		for (int i = 0; i < targetElements.getLength(); i++) {
			Element targetElement = (Element)targetElements.item(i);				
			Target target = xml2Target(targetElement);
			targets.getChildren().add(target);          				
		}
		// Join condition
		Element joinConditionElement = getBPELChildElementByLocalName(targetsElement, "joinCondition");
		if (joinConditionElement != null) {
			targets.setJoinCondition(xml2Condition(joinConditionElement));
		}
		return targets;
     }
     
	protected Target xml2Target(Element targetElement) {
		
		final Target target = BPELFactory.eINSTANCE.createTarget();
				
		// Save all the references to external namespaces		
		saveNamespacePrefix(target, targetElement);
		
		xml2ExtensibleElement(target, targetElement);

		if (targetElement.hasAttribute("linkName")) {
			final String linkName = targetElement.getAttribute("linkName");			
			process.getPostLoadRunnables().add(new Runnable() {
				public void run() {
					Link link = BPELUtils.getLink(target.getActivity(), linkName);
					if (link != null)
						target.setLink(link);
					else
						target.setLink(new LinkProxy(resource.getURI(), linkName));
				}
			});
		}
		return target;		
	}
	
	protected Sources xml2Sources(Element sourcesElement) {
		Sources sources = BPELFactory.eINSTANCE.createSources();
		NodeList sourceElements = getBPELChildElementsByLocalName(sourcesElement, "source");
		for (int i = 0; i < sourceElements.getLength(); i++) {
			Element sourceElement = (Element)sourceElements.item(i);
			Source source = xml2Source(sourceElement);
			sources.getChildren().add(source);          				
		}
		return sources;
	}
	
	protected Source xml2Source(Element sourceElement) {
		final String linkName = sourceElement.getAttribute("linkName");		
		final Source source = BPELFactory.eINSTANCE.createSource();
		
		// Save all the references to external namespaces		
		saveNamespacePrefix(source, sourceElement);
		
		// Read transitionCondition element
		Element transitionConditionElement = getBPELChildElementByLocalName(sourceElement, "transitionCondition");
		if (transitionConditionElement != null) {
			Condition transitionCondition = xml2Condition(transitionConditionElement);
			source.setTransitionCondition(transitionCondition);
		}
		
		
		xml2ExtensibleElement(source, sourceElement);
		
		process.getPostLoadRunnables().add(new Runnable() {
			public void run() {
				Link link = BPELUtils.getLink(source.getActivity(), linkName);
				if (link != null)
					source.setLink(link);
				else
					source.setLink(new LinkProxy(resource.getURI(), linkName));
			}
		});
		return source;							
	}	
	
	/**
	 * Converts an XML scope element to a BPEL Scope object.
	 */
	protected Activity xml2Scope(Element scopeElement) {
    	Scope scope = BPELFactory.eINSTANCE.createScope();
		if (scopeElement == null) return scope;

		Attr name = scopeElement.getAttributeNode("name");
		
		if (name != null && name.getSpecified()) {
			scope.setName(name.getValue());
		}
				
		Attr isolated = scopeElement.getAttributeNode("isolated");
		
		if (isolated != null && isolated.getSpecified())
			scope.setIsolated(new Boolean(isolated.getValue().equals("yes")));
				
		// Handle Variables element
		Element variablesElement = getBPELChildElementByLocalName(scopeElement, "variables");
		if (variablesElement != null) {
			Variables variables = xml2Variables(variablesElement);
			scope.setVariables(variables);
		}
				
		// Handle CorrelationSet element
		Element correlationSetsElement = getBPELChildElementByLocalName(scopeElement, "correlationSets");
		if (correlationSetsElement != null) {
			CorrelationSets correlationSets = xml2CorrelationSets(correlationSetsElement);
			scope.setCorrelationSets(correlationSets);
		}
		
		// Handle PartnerLinks element
		Element partnerLinksElement = getBPELChildElementByLocalName(scopeElement, "partnerLinks");
		if (partnerLinksElement != null) {
			PartnerLinks partnerLinks = xml2PartnerLinks(partnerLinksElement);
			scope.setPartnerLinks(partnerLinks);
		}
				
		// Handle FaultHandler element
        Element faultHandlerElement = getBPELChildElementByLocalName(scopeElement, "faultHandlers");
        if (faultHandlerElement != null) {               		
			FaultHandler faultHandler =	xml2FaultHandler(faultHandlerElement); 
			scope.setFaultHandlers(faultHandler);
        }

		// Handle CompensationHandler element
		setCompensationHandler(scopeElement, scope);
		
		// Handler EventHandler element
		setEventHandler(scopeElement, scope);
		
		setStandardAttributes(scopeElement, scope);

		// Handle activities 
        NodeList scopeElements = scopeElement.getChildNodes();
        
        Element activityElement = null;

		if (scopeElements != null && scopeElements.getLength() > 0) {
          
           for (int i = 0; i < scopeElements.getLength(); i++) {
				if (scopeElements.item(i).getNodeType() != Node.ELEMENT_NODE)
           	   	  continue;
           	   	             	
               	activityElement = (Element)scopeElements.item(i); 
               
				if (activityElement.getLocalName().equals("faultHandlers") || 
					activityElement.getLocalName().equals("compensationHandler"))
					continue;
               
               Activity activity = xml2Activity(activityElement);
               if (activity != null) 
               	scope.setActivity(activity); 
           }
        }
        		
        return scope;
	}

	/**
	 * Converts an XML flow element to a BPEL Flow object.
	 */
	protected Activity xml2Flow(Element flowElement) {
    	Flow flow = BPELFactory.eINSTANCE.createFlow();
		if (flowElement == null) return flow;		
		Attr name = flowElement.getAttributeNode("name");
		
		if (name != null && name.getSpecified()) 
			flow.setName(name.getValue());
		
		Element linksElement = getBPELChildElementByLocalName(flowElement, "links");
		if (linksElement != null) {
			Links links = xml2Links(linksElement);
			flow.setLinks(links);
		}
			        
        setStandardAttributes(flowElement, flow);
        
        NodeList flowElements = flowElement.getChildNodes();
        
        Element activityElement = null;

		if (flowElements != null && flowElements.getLength() > 0) {
          
           for (int i = 0; i < flowElements.getLength(); i++) {
				if ((flowElements.item(i).getNodeType() != Node.ELEMENT_NODE) || 
				     ((Element)flowElements.item(i)).getLocalName().equals("links"))
           	   	  continue;
           	   	             	
               activityElement = (Element)flowElements.item(i); 
               Activity activity = xml2Activity(activityElement);
               if (activity != null) 
               	flow.getActivities().add(activity); 
           }
        }
		
		return flow;
	}

	protected Links xml2Links(Element linksElement) {
		if (!linksElement.getLocalName().equals("links"))
			return null;
			
		Links links = BPELFactory.eINSTANCE.createLinks();

		// Save all the references to external namespaces		
		saveNamespacePrefix(links, linksElement);
		
		BPELNodeList linkElements = getBPELChildElementsByLocalName(linksElement, "link");
		for (int i=0; i < linkElements.getLength(); i++) {
			Element linkElement = (Element)linkElements.item(i);
			Link link = xml2Link(linkElement);
			links.getChildren().add(link);
		}
		
		// extensibility elements
		xml2ExtensibleElement(links, linksElement);
		
		return links; 	
	}
	
	/**
	 * Converts an XML link element to a BPEL Link object.
	 */
	protected Link xml2Link(Element linkElement) {
		Link link = BPELFactory.eINSTANCE.createLink();
		
		// Save all the references to external namespaces		
		saveNamespacePrefix(link, linkElement);
		
		if (linkElement == null) return link;

		Attr name = linkElement.getAttributeNode("name");
		
		if (name != null && name.getSpecified())
			link.setName(name.getValue());
		
		xml2ExtensibleElement(link,linkElement); 

		return link;		
	}

	/**
	 * Converts an XML pick element to a BPEL Pick object.
	 */
	protected Activity xml2Pick(Element pickElement) {
    	Pick pick = BPELFactory.eINSTANCE.createPick();
		if (pickElement == null) return pick;

		// Set name
		Attr name = pickElement.getAttributeNode("name");
		
		if (name != null && name.getSpecified())
			pick.setName(name.getValue());
		
		// Set createInstance
		Attr createInstance = pickElement.getAttributeNode("createInstance");
		
		if (createInstance != null && createInstance.getSpecified()) 
       		pick.setCreateInstance(Boolean.valueOf(createInstance.getValue().equals("yes") ? "True":"False"));  	
	
        NodeList pickElements = pickElement.getChildNodes();
        
        Element pickInstanceElement = null;

		if (pickElements != null && pickElements.getLength() > 0) {
          
           for (int i = 0; i < pickElements.getLength(); i++) {
				if (pickElements.item(i).getNodeType() != Node.ELEMENT_NODE)
           	   	  continue;
           	   	             	
               pickInstanceElement = (Element)pickElements.item(i);
               
				if (pickInstanceElement.getLocalName().equals("onAlarm")) {
     				OnAlarm onAlarm = xml2OnAlarm((Element)pickInstanceElement);
     				
     				pick.getAlarm().add(onAlarm);
     			}     	
				else
					if (pickInstanceElement.getLocalName().equals("onMessage")) {
     					OnMessage onMessage = xml2OnMessage((Element)pickInstanceElement);
	     				
    	 				pick.getMessages().add(onMessage);
     				}     
           }
        }
        
        setStandardAttributes(pickElement, pick);

		return pick;
	}

	/**
	 * Converts an XML eventHandler element to a BPEL eventHandler object.
	 */
	protected EventHandler xml2EventHandler(Element eventHandlerElement) {
		EventHandler eventHandler = BPELFactory.eINSTANCE.createEventHandler();
		
		// Save all the references to external namespaces		
		saveNamespacePrefix(eventHandler, eventHandlerElement);
		
		if (eventHandlerElement == null) return eventHandler;
	
		NodeList eventHandlerElements = eventHandlerElement.getChildNodes();        
		Element eventHandlerInstanceElement = null;
		if (eventHandlerElements != null && eventHandlerElements.getLength() > 0) {
          
			for (int i = 0; i < eventHandlerElements.getLength(); i++) {
				if (eventHandlerElements.item(i).getNodeType() != Node.ELEMENT_NODE)
					continue;           	   	             
			   	eventHandlerInstanceElement = (Element)eventHandlerElements.item(i);
               
				if (eventHandlerInstanceElement.getLocalName().equals("onAlarm")) {
					OnAlarm onAlarm = xml2OnAlarm((Element)eventHandlerInstanceElement);     				
					eventHandler.getAlarm().add(onAlarm);
				}   
				else if (eventHandlerInstanceElement.getLocalName().equals("onEvent")) {
					OnEvent onEvent = xml2OnEvent((Element)eventHandlerInstanceElement);	     				
					eventHandler.getEvents().add(onEvent);
				}  
			}
		}       
		
		xml2ExtensibleElement(eventHandler, eventHandlerElement); 
		return eventHandler;
	}

	/**
	 * Converts an XML onMessage element to a BPEL OnMessage object.
	 */
	protected OnMessage xml2OnMessage(Element onMessageElement) {
 		OnMessage onMessage = BPELFactory.eINSTANCE.createOnMessage();

		// Save all the references to external namespaces		
		saveNamespacePrefix(onMessage, onMessageElement);
 		
		if (onMessageElement == null) return onMessage;

		// Set several parms
		setOperationParmsOnMessage(onMessageElement, onMessage);
				
		// Set activity
		onMessage.setActivity(getChildActivity(onMessageElement));

		// Set the FromPart
		BPELNodeList fromPartElements = getBPELChildElementsByLocalName(onMessageElement, "fromPart");
		Iterator it = fromPartElements.iterator();
		while (it.hasNext()) {
			Element fromPartElement = (Element)it.next();
			if (BPELUtils.isBPELNamespace(fromPartElement.getNamespaceURI())) {
				FromPart fromPart = xml2FromPart(fromPartElement);
				onMessage.getFromPart().add(fromPart);
			}
		}		

		xml2ExtensibleElement(onMessage, onMessageElement);
				
		return onMessage;
	}

	/**
	 * Converts an XML onEvent element to a BPEL OnEvent object.
	 */
	protected OnEvent xml2OnEvent(Element onEventElement) {
		OnEvent onEvent = BPELFactory.eINSTANCE.createOnEvent();

		// Save all the references to external namespaces		
		saveNamespacePrefix(onEvent, onEventElement);
 		
		if (onEventElement == null) return onEvent;

		// Set several parms
		setOperationParmsOnEvent(onEventElement, onEvent);
				
		// Set activity
		onEvent.setActivity(getChildActivity(onEventElement));

		// Set the FromPart
		BPELNodeList fromPartElements = getBPELChildElementsByLocalName(onEventElement, "fromPart");
		Iterator it = fromPartElements.iterator();
		while (it.hasNext()) {
			Element fromPartElement = (Element)it.next();
			if (BPELUtils.isBPELNamespace(fromPartElement.getNamespaceURI())) {
				FromPart fromPart = xml2FromPart(fromPartElement);
				onEvent.getFromPart().add(fromPart);
			}
		}		
		
		xml2ExtensibleElement(onEvent, onEventElement);
				
		return onEvent;
	}

	/**
	 * Converts an XML onAlarm element to a BPEL OnAlarm object.
	 */
	protected OnAlarm xml2OnAlarm(Element onAlarmElement) {
   		OnAlarm onAlarm = BPELFactory.eINSTANCE.createOnAlarm();

		// Save all the references to external namespaces		
		saveNamespacePrefix(onAlarm, onAlarmElement);
   		
		if (onAlarmElement == null) return onAlarm;
		
		// Set for element
		Element forElement = getBPELChildElementByLocalName(onAlarmElement, "for");
		if (forElement != null) {
			Expression expression = xml2Expression(forElement);
			onAlarm.setFor(expression);
		}
		
		// Set until element
		Element untilElement = getBPELChildElementByLocalName(onAlarmElement, "until");
		if (untilElement != null) {
			Expression expression = xml2Expression(untilElement);
			onAlarm.setUntil(expression);
		}
		
		// Set repeatEvery element
		Element repeatEveryElement = getBPELChildElementByLocalName(onAlarmElement, "repeatEvery");
		if (repeatEveryElement != null) {
			Expression expression = xml2Expression(repeatEveryElement);
			onAlarm.setRepeatEvery(expression);
		}
		
		// Set activity
		onAlarm.setActivity(getChildActivity(onAlarmElement));
		
		xml2ExtensibleElement(onAlarm, onAlarmElement);		
			
		return onAlarm;					
	}

	/**
	 * Converts an XML while element to a BPEL While object.
	 */
	protected Activity xml2While(Element whileElement) {
    	While _while = BPELFactory.eINSTANCE.createWhile();
		if (whileElement == null) return _while;

		// Handle condition element
		Element conditionElement = getBPELChildElementByLocalName(whileElement, "condition");
		if (conditionElement != null) {
			Condition condition = xml2Condition(conditionElement);
			_while.setCondition(condition);
		}

        NodeList whileElements = whileElement.getChildNodes();
        
        Element activityElement = null;

		if (whileElements != null && whileElements.getLength() > 0) {
			for (int i = 0; i < whileElements.getLength(); i++) {			
				if (whileElements.item(i).getNodeType() != Node.ELEMENT_NODE)
           	   	  continue;
           	   	  			
				activityElement = (Element)whileElements.item(i); 
            	Activity activity = xml2Activity(activityElement);
            	if (activity != null) 
         	   		_while.setActivity(activity); 
			}
        }
        
        setStandardAttributes(whileElement, _while);
		
		return _while;
	}

	/**
	 * Converts an XML switch element to a BPEL Switch object.
	 */
	protected Activity xml2Switch(Element switchElement) {
    	Switch _switch = BPELFactory.eINSTANCE.createSwitch();
		if (switchElement == null) return _switch;


		// Handle case
		NodeList caseElements = getBPELChildElementsByLocalName(switchElement, "case");
                 
		if (caseElements != null && caseElements.getLength() > 0) {
           for (int i = 0; i < caseElements.getLength(); i++) {			
				Case _case = xml2Case((Element)caseElements.item(i)); 
				_switch.getCases().add(_case);
           }
        }

		// Handle otherwise
		Element otherwiseElement = getBPELChildElementByLocalName(switchElement, "otherwise");
		if (otherwiseElement != null) {
			Otherwise otherwise = xml2Otherwise(otherwiseElement);
			_switch.setOtherwise(otherwise);
		}
		
		setStandardAttributes(switchElement, _switch);
		
		return _switch;		
	}

	/**
	 * Converts an XML case element to a BPEL Case object.
	 */
	protected Case xml2Case(Element caseElement) {
    	Case _case = BPELFactory.eINSTANCE.createCase();
    	
		// Save all the references to external namespaces		
		saveNamespacePrefix(_case, caseElement);
    	
		if (caseElement == null) return _case;

		// Handle condition element
		Element conditionElement = getBPELChildElementByLocalName(caseElement, "condition");
		if (conditionElement != null) {
			Condition condition = xml2Condition(conditionElement);
			_case.setCondition(condition);
		}

		// Set activity
		Activity activity = getChildActivity(caseElement);
		if (activity != null) {
			_case.setActivity(activity);
		}
		
		xml2ExtensibleElement(_case,caseElement);
			  
		return _case;
	}

	/**
	 * Converts an XML condition element to a BPEL Condition object.
	 */
	protected Condition xml2Condition(Element conditionElement) {
		Condition condition = BPELFactory.eINSTANCE.createCondition();
    	
		// Save all the references to external namespaces		
		saveNamespacePrefix(condition, conditionElement);
    	
		if (conditionElement == null) return condition;

		if (conditionElement.hasAttribute("expressionLanguage")) {
			// Set expressionLanguage
			condition.setExpressionLanguage(conditionElement.getAttribute("expressionLanguage"));
		}
		
		// Determine whether or not there is an element in the child list.
		Node candidateChild = null;
		NodeList nodeList = conditionElement.getChildNodes();
		int length = nodeList.getLength();
		for (int i = 0; i < length; i++) {
			Node child = nodeList.item(i);
			if (child.getNodeType() == Node.ELEMENT_NODE) {
				candidateChild = child;
				break;
			}
		}
		if (candidateChild == null) {
			candidateChild = conditionElement.getFirstChild();
		}
		String data = getText(candidateChild);
		
		if (data == null) {
			// No text or CDATA node. If it's an element node, then
			// deserialize and install.
			if (candidateChild != null && candidateChild.getNodeType() == Node.ELEMENT_NODE) {
				// Look if there's an ExtensibilityElement deserializer for this element
				Element childElement = (Element)candidateChild;
				QName qname = new QName(childElement.getNamespaceURI(), childElement.getLocalName());
				BPELExtensionDeserializer deserializer=null;
				try {
					deserializer = (BPELExtensionDeserializer)extensionRegistry.queryDeserializer(ExtensibleElement.class,qname);
				} catch (WSDLException e) {}
				if (deserializer!=null) {
					// Deserialize the DOM element and add the new Extensibility element to the parent
					// ExtensibleElement
					try {
						Map nsMap = getAllNamespacesForElement(conditionElement);
						ExtensibilityElement extensibilityElement=deserializer.unmarshall(ExtensibleElement.class,qname,childElement,process,nsMap,extensionRegistry,resource.getURI());
						condition.setBody(extensibilityElement);
					} catch (WSDLException e) {
						throw new WrappedException(e);
					}
				}
			}			
		} else {
			condition.setBody(data);
		}

		return condition;
	}

	/**
	 * Converts an XML expression element to a BPEL Expression object.
	 */
	protected Expression xml2Expression(Element expressionElement) {
		Expression expression = BPELFactory.eINSTANCE.createExpression();
    	
		// Save all the references to external namespaces		
		saveNamespacePrefix(expression, expressionElement);
    	
		if (expressionElement == null) return expression;

		// Set expressionLanguage
		if (expressionElement.hasAttribute("expressionLanguage")) {
			expression.setExpressionLanguage(expressionElement.getAttribute("expressionLanguage"));
		}

		// Set opaque
		if (expressionElement.hasAttribute("opaque")) {
			expression.setOpaque(new Boolean(expressionElement.getAttribute("opaque").equals("yes")));
		}
		
		// Determine whether or not there is an element in the child list.
		Node candidateChild = null;
		NodeList nodeList = expressionElement.getChildNodes();
		int length = nodeList.getLength();
		for (int i = 0; i < length; i++) {
			Node child = nodeList.item(i);
			if (child.getNodeType() == Node.ELEMENT_NODE) {
				candidateChild = child;
				break;
			}
		}
		if (candidateChild == null) {
			candidateChild = expressionElement.getFirstChild();
		}
		String data = getText(candidateChild);
		
		if (data == null) {
			// No text or CDATA node. If it's an element node, then
			// deserialize and install.
			if (candidateChild != null && candidateChild.getNodeType() == Node.ELEMENT_NODE) {
				// Look if there's an ExtensibilityElement deserializer for this element
				Element childElement = (Element)candidateChild;
				QName qname = new QName(childElement.getNamespaceURI(), childElement.getLocalName());
				BPELExtensionDeserializer deserializer=null;
				try {
					deserializer = (BPELExtensionDeserializer)extensionRegistry.queryDeserializer(ExtensibleElement.class,qname);
				} catch (WSDLException e) {}
				if (deserializer!=null) {
					// Deserialize the DOM element and add the new Extensibility element to the parent
					// ExtensibleElement
					try {
						Map nsMap = getAllNamespacesForElement(expressionElement);
						ExtensibilityElement extensibilityElement=deserializer.unmarshall(ExtensibleElement.class,qname,childElement,process,nsMap,extensionRegistry,resource.getURI());
						expression.setBody(extensibilityElement);
					} catch (WSDLException e) {
						throw new WrappedException(e);
					}
				}
			}			
		} else {
			expression.setBody(data);
		}

		return expression;
	}

	protected Otherwise xml2Otherwise(Element otherwiseElement) {
		Otherwise otherwise = BPELFactory.eINSTANCE.createOtherwise();
		
		// Save all the references to external namespaces		
		saveNamespacePrefix(otherwise, otherwiseElement);
		
		Activity activity = getChildActivity(otherwiseElement);
		otherwise.setActivity(activity);
		xml2ExtensibleElement(otherwise, otherwiseElement);
		
		return otherwise;
	}

	/**
	 * Converts an XML sequence element to a BPEL Sequence object.
	 */
	protected Activity xml2Sequence(Element sequenceElement) {
    	Sequence sequence = BPELFactory.eINSTANCE.createSequence();
		if (sequenceElement == null) return sequence;
	
		// Set name
		Attr name = sequenceElement.getAttributeNode("name");
		
		if (name != null && name.getSpecified())
			sequence.setName(name.getValue());
		
        NodeList sequenceElements = sequenceElement.getChildNodes();
        
        Element activityElement = null;

		if (sequenceElements != null && sequenceElements.getLength() > 0) {
          
           for (int i = 0; i < sequenceElements.getLength(); i++) {
			if (sequenceElements.item(i).getNodeType() != Node.ELEMENT_NODE)
           	   	  continue;
           	   	             	
               activityElement = (Element)sequenceElements.item(i); 
               Activity activity = xml2Activity(activityElement);
               if (activity != null) 
               	sequence.getActivities().add(activity); 
           }
        }
        
        setStandardAttributes(sequenceElement, sequence);
		
		return sequence;
	}

	/**
	 * Converts an XML empty element to a BPEL Empty object.
	 */
	protected Activity xml2Empty(Element emptyElement) {
		Empty empty = BPELFactory.eINSTANCE.createEmpty();
		
		setStandardAttributes(emptyElement, empty);
		 
    	return empty;
	}

	/**
	 * Converts an XML opaqueActivity element to a BPEL OpaqueActivity object.
	 */
	protected Activity xml2OpaqueActivity(Element opaqueActivityElement) {
		OpaqueActivity opaqueActivity = BPELFactory.eINSTANCE.createOpaqueActivity();
		
		setStandardAttributes(opaqueActivityElement, opaqueActivity);
		 
    	return opaqueActivity;
	}

	/**
	 * Converts an XML rethrow element to a BPEL Rethrow object.
	 */
	protected Activity xml2Rethrow(Element rethrowElement) {
		Rethrow rethrow = BPELFactory.eINSTANCE.createRethrow();
		
		setStandardAttributes(rethrowElement, rethrow);
		 
    	return rethrow;
	}

	/**
	 * Converts an XML extensionactivity element to a BPEL ExtensionActivity object.
	 */
	protected Activity xml2ExtensionActivity(Element extensionActivityElement) {
		ExtensionActivity extensionActivity = BPELFactory.eINSTANCE.createExtensionActivity();
		
		setStandardAttributes(extensionActivityElement, extensionActivity);
		 
    	return extensionActivity;
	}

	
	/**
	 * Converts an XML wait element to a BPEL Wait object.
	 */
	protected Activity xml2Wait(Element waitElement) {
    	Wait wait = BPELFactory.eINSTANCE.createWait();
		if (waitElement == null) return wait;
		
		// Set name
		Attr name = waitElement.getAttributeNode("name");
		
		if (name != null && name.getSpecified()) 
			wait.setName(name.getValue());
		
		// Set for element
		Element forElement = getBPELChildElementByLocalName(waitElement, "for");
		if (forElement != null) {
			Expression expression = xml2Expression(forElement);
			wait.setFor(expression);
		}
		
		// Set until element
		Element untilElement = getBPELChildElementByLocalName(waitElement, "until");
		if (untilElement != null) {
			Expression expression = xml2Expression(untilElement);
			wait.setUntil(expression);
		}
		
		setStandardAttributes(waitElement, wait);
			
		return wait;						
	}

	/**
	 * Converts an XML terminate element to a BPEL Terminate object.
	 */
	protected Activity xml2Terminate(Element terminateElement) {
    	Terminate terminate = BPELFactory.eINSTANCE.createTerminate();

		Attr name = terminateElement.getAttributeNode("name");
		
		if (name != null && name.getSpecified())
			terminate.setName(name.getValue());
		
		setStandardAttributes(terminateElement, terminate);
			
		return terminate;
	}

	/**
	 * Converts an XML throw element to a BPEL Throw object.
	 */
	protected Activity xml2Throw(Element throwElement) {
		Throw _throw = BPELFactory.eINSTANCE.createThrow();
		if (throwElement == null) return _throw;
		
		if (throwElement.hasAttribute("name")) {
			_throw.setName(throwElement.getAttribute("name"));
		}
		if (throwElement.hasAttribute("faultName")) {
			QName qName = BPELUtils.createAttributeValue(throwElement, "faultName");	
			_throw.setFaultName(qName);
		}

		// Set fault variable name
		setVariable(throwElement, _throw, "faultVariable", BPELPackage.eINSTANCE.getThrow_FaultVariable());
		
		setStandardAttributes(throwElement, _throw);
		
		return _throw;	
	}

	/**
	 * Converts an XML assign element to a BPEL Assign object.
	 */
	protected Activity xml2Assign(Element assignElement) {
		Assign assign = BPELFactory.eINSTANCE.createAssign();
		if (assignElement == null) return assign;
        
        List copies = getBPELChildElementsByLocalName(assignElement, "copy");
        for (int i = 0; i < copies.size(); i++) {
            Copy copy = xml2Copy((Element) copies.get(i));
            assign.getCopy().add(copy);
        }
        
        setStandardAttributes(assignElement, assign);

		return assign;
	}

	/**
	 * Converts an XML copy element to a BPEL Copy object.
	 */
	protected Copy xml2Copy(Element copyElement) {
		Copy copy = BPELFactory.eINSTANCE.createCopy();
        if (copyElement == null) return copy;

		// Save all the references to external namespaces		
		saveNamespacePrefix(copy, copyElement);

        Element fromElement = getBPELChildElementByLocalName(copyElement, "from");
        if (fromElement != null) {
            From from = BPELFactory.eINSTANCE.createFrom();
            xml2From(from, fromElement); 
            copy.setFrom(from);
        }
        
        Element toElement = getBPELChildElementByLocalName(copyElement, "to");
        if (toElement != null) {
            To to = BPELFactory.eINSTANCE.createTo();
            xml2To(to, toElement); 
            copy.setTo(to);
        }
 
 		xml2ExtensibleElement(copy, copyElement);
 		
		return copy;
	}

	/**
	 * Converts an XML toPart element to a BPEL ToPart object.
	 */
	protected ToPart xml2ToPart(Element toPartElement) {
		ToPart toPart = BPELFactory.eINSTANCE.createToPart();
        if (toPartElement == null) return toPart;

		// Save all the references to external namespaces		
		saveNamespacePrefix(toPart, toPartElement);

		// Handle part attribute
		if (toPartElement.hasAttribute("part")) 
			toPart.setPart(toPartElement.getAttribute("part"));

		// Handle from-spec
        Element fromElement = getBPELChildElementByLocalName(toPartElement, "from");
        if (fromElement != null) {
            From from = BPELFactory.eINSTANCE.createFrom();
            xml2From(from, fromElement); 
            toPart.setFrom(from);
        }
        
        
		return toPart;
	}

	/**
	 * Converts an XML fromPart element to a BPEL FromPart object.
	 */
	protected FromPart xml2FromPart(Element fromPartElement) {
		FromPart fromPart = BPELFactory.eINSTANCE.createFromPart();
        if (fromPartElement == null) return fromPart;

		// Save all the references to external namespaces		
		saveNamespacePrefix(fromPart, fromPartElement);

		// Handle part attribute
		if (fromPartElement.hasAttribute("part")) 
			fromPart.setPart(fromPartElement.getAttribute("part"));

		// Handle to-spec
		Element toElement = getBPELChildElementByLocalName(fromPartElement, "to");
        if (toElement != null) {
            To to = BPELFactory.eINSTANCE.createTo();
            xml2To(to, toElement); 
            fromPart.setTo(to);
        }
        
		return fromPart;
	}

	/**
	 * Converts an XML "to" element to a BPEL To object.
	 */
	protected void xml2To(To to, Element toElement) {
		// Save all the references to external namespaces		
		saveNamespacePrefix(to, toElement);
		
		// Set variable
		Attr variable = toElement.getAttributeNode("variable"); 
    
		if (variable != null && variable.getSpecified())				
			setVariable(toElement, to, "variable", BPELPackage.eINSTANCE.getTo_Variable());

		// Set part
		Attr part = toElement.getAttributeNode("part"); 		
    
		if (part != null && part.getSpecified()) {		
			final String partAttr = toElement.getAttribute("part");
            ((ToImpl) to).setPartName(partAttr);
		}

		// Set partnerLink			
		Attr partnerLink = toElement.getAttributeNode("partnerLink");			
		
		if (partnerLink != null && partnerLink.getSpecified())
			setPartnerLink(toElement, to, BPELPackage.eINSTANCE.getTo_PartnerLink());			

		// Set property		
		Attr property = toElement.getAttributeNode("property");
     		
		if (property != null && property.getSpecified()) {
			setProperties(toElement, to, "property");
		}

		// Set query element
		Element queryElement = getBPELChildElementByLocalName(toElement, "query");
		if (queryElement != null) {
			Query queryObject = BPELFactory.eINSTANCE.createQuery();
			to.setQuery(queryObject);
			
			// Set queryLanguage
			if (queryElement.hasAttribute("queryLanguage")) {
				String queryLanguage = queryElement.getAttribute("queryLanguage");
				queryObject.setQueryLanguage(queryLanguage);
			}

			// Set query text
			// Get the condition text
			String data = "";
			Node node = queryElement.getFirstChild();
			boolean containsValidData = false;
			while (node != null) {
				if (node.getNodeType() == Node.TEXT_NODE) {
					Text text = (Text)node;
					data += text.getData();
				} else if (node.getNodeType() == Node.CDATA_SECTION_NODE) {
					data="";
					do {
						CDATASection cdata = (CDATASection) node;
						data += cdata.getData();
						node = node.getNextSibling();
						containsValidData = true;
					} while (node != null && node.getNodeType() == Node.CDATA_SECTION_NODE);
					break;
				}
				node = node.getNextSibling();
			}
			if (!containsValidData) {
				for (int i = 0; i < data.length(); i++) {
					char charData = data.charAt(i);
					if (charData == '\n' || Character.isWhitespace(charData)){}//ignore
					else { //valid data
						containsValidData = true;
						break;
					}
				}
			}
			
			if (containsValidData) {
				queryObject.setValue(data);
			}
		}
	}

	/**
	 * Converts an XML "from" element to a BPEL From object.
	 */
	protected void xml2From(From from, Element fromElement) {
		xml2To(from,fromElement);
		
		Attr endpointReference = fromElement.getAttributeNode("endpointReference");
    
		if (endpointReference != null && endpointReference.getSpecified())
			from.setEndpointReference(EndpointReferenceRole.get(endpointReference.getValue()));
		
		// Set service-ref element
		boolean foundServiceRef = false;
		Element serviceRefElement = getBPELChildElementByLocalName(fromElement, "service-ref");
		if (serviceRefElement != null) {
			foundServiceRef = true;
			ServiceRef serviceRef = BPELFactory.eINSTANCE.createServiceRef();
			from.setServiceRef(serviceRef);
			
			// Set reference scheme
			if (serviceRefElement.hasAttribute("reference-scheme")) {
				String scheme = serviceRefElement.getAttribute("reference-scheme");
				serviceRef.setReferenceScheme(scheme);
			}
			
			// Set the value of the service reference

			// Determine whether or not there is an element in the child list.
			Node candidateChild = null;
			NodeList nodeList = serviceRefElement.getChildNodes();
			int length = nodeList.getLength();
			for (int i = 0; i < length; i++) {
				Node child = nodeList.item(i);
				if (child.getNodeType() == Node.ELEMENT_NODE) {
					candidateChild = child;
					break;
				}
			}
			if (candidateChild == null) {
				candidateChild = serviceRefElement.getFirstChild();
			}
			String data = getText(candidateChild);
			
			if (data == null) {
				// No text or CDATA node. If it's an element node, then
				// deserialize and install.
				if (candidateChild != null && candidateChild.getNodeType() == Node.ELEMENT_NODE) {
					// Look if there's an ExtensibilityElement deserializer for this element
					Element childElement = (Element)candidateChild;
					QName qname = new QName(childElement.getNamespaceURI(), childElement.getLocalName());
					BPELExtensionDeserializer deserializer=null;
					try {
						deserializer = (BPELExtensionDeserializer)extensionRegistry.queryDeserializer(ExtensibleElement.class,qname);
					} catch (WSDLException e) {}
					if (deserializer != null && !(deserializer instanceof BPELUnknownExtensionDeserializer)) {
						// Deserialize the DOM element and add the new Extensibility element to the parent
						// ExtensibleElement
						try {
							Map nsMap = getAllNamespacesForElement(serviceRefElement);
							ExtensibilityElement extensibilityElement=deserializer.unmarshall(ExtensibleElement.class,qname,childElement,process,nsMap,extensionRegistry,resource.getURI());
							serviceRef.setValue(extensibilityElement);
						} catch (WSDLException e) {
							throw new WrappedException(e);
						}
					} else {
						ServiceReferenceDeserializer referenceDeserializer = extensionRegistry.getServiceReferenceDeserializer(serviceRef.getReferenceScheme());
						if (referenceDeserializer != null) {
							Object serviceReference = referenceDeserializer.unmarshall(childElement, process);
							serviceRef.setValue(serviceReference);
						}
					}
				}
			} else {
				serviceRef.setValue(data);
			}
		}
		
		// Set new expression element
		Element expressionElement = getBPELChildElementByLocalName(fromElement, "expression");
		if (expressionElement != null) {
			Expression expressionObject = BPELFactory.eINSTANCE.createExpression();
			from.setExpression(expressionObject);
			
			// Set expressionLanguage
			if (expressionElement.hasAttribute("expressionLanguage")) {
				expressionObject.setExpressionLanguage(expressionElement.getAttribute("expressionLanguage"));
			}

			// Set expression text
			// Get the condition text
			String data = "";
			Node node = expressionElement.getFirstChild();
			boolean containsValidData = false;
			while (node != null) {
				if (node.getNodeType() == Node.TEXT_NODE) {
					Text text = (Text)node;
					data += text.getData();
				} else if (node.getNodeType() == Node.CDATA_SECTION_NODE) {
					data="";
					do {
						CDATASection cdata = (CDATASection) node;
						data += cdata.getData();
						node = node.getNextSibling();
						containsValidData = true;
					} while (node != null && node.getNodeType() == Node.CDATA_SECTION_NODE);
					break;
				}
				node = node.getNextSibling();
			}
			if (!containsValidData) {
				for (int i = 0; i < data.length(); i++) {
					char charData = data.charAt(i);
					if (charData == '\n' || Character.isWhitespace(charData)){}//ignore
					else { //valid data
						containsValidData = true;
						break;
					}
				}
			}
			
			if (containsValidData) {
				expressionObject.setBody(data);
			}
		}

		// Set opaque
		Attr opaque = fromElement.getAttributeNode("opaque");
			
		if (opaque != null && opaque.getSpecified())
			from.setOpaque(new Boolean(opaque.getValue().equals("yes")));

		// Literal value
		// Only consider a literal if we do not have a service-ref or
		// a query or an expression.
		// Revisit this with resolution of OASIS issue.
		if (!foundServiceRef && from.getQuery() == null && from.getExpression() == null) {
			String elementData = null;
			for (Node node = fromElement.getFirstChild(); node != null; node = node.getNextSibling()) {
				if (node.getNodeType() == Node.ELEMENT_NODE) {
					if (elementData == null) elementData = "";
					elementData = elementData+BPELUtils.elementToString((Element)node);
				}
			}
			
			from.setUnsafeLiteral(Boolean.FALSE);
			boolean containsValidData = false;
			String data = "";
			if (elementData != null) {
				from.setUnsafeLiteral(Boolean.TRUE);
				data = elementData;
				containsValidData = true;
			} else {
				Node node = fromElement.getFirstChild();
				while(node != null) {
					if (node.getNodeType() == Node.TEXT_NODE) {
						Text text = (Text) node;
						data += text.getData();
						from.setUnsafeLiteral(Boolean.TRUE);
					} else if (node.getNodeType() == Node.CDATA_SECTION_NODE) {
						data="";
						do {
							CDATASection cdata = (CDATASection) node;
							data += cdata.getData();
							node = node.getNextSibling();
							containsValidData = true;
						} while (node != null && node.getNodeType() == Node.CDATA_SECTION_NODE);
						break;
					}
					node = node.getNextSibling();
				}
	
				if(!containsValidData){
					for (int i = 0; i < data.length(); i++) {
						char charData = data.charAt(i);
						if(charData == '\n' || Character.isWhitespace(charData)){}//ignore
						else{ //valid data
							containsValidData = true;
							break;
						}
					}
				}
			}
			if(containsValidData) from.setLiteral(data);
		}
		// See if there is an xsi:type attribue.
		if (fromElement.hasAttribute("xsi:type")) {
			QName qName = BPELUtils.createAttributeValue(fromElement, "xsi:type");
			XSDTypeDefinition type = new XSDTypeDefinitionProxy(resource.getURI(), qName);
			from.setType(type);						
		}
	}


	/**
	 * Converts an XML import element to a BPEL Import object.
	 */
	protected Import xml2Import(Element importElement) {
		if (!importElement.getLocalName().equals("import"))
			return null;
			
		Import imp = BPELFactory.eINSTANCE.createImport();

		// Save all the references to external namespaces		
		saveNamespacePrefix(imp, importElement);
		
		// namespace
		if (importElement.hasAttribute("namespace"))
			imp.setNamespace(importElement.getAttribute("namespace"));
		
		// location
		if (importElement.hasAttribute("location"))
			imp.setLocation(importElement.getAttribute("location"));
		
		// importType
		if (importElement.hasAttribute("importType"))
			imp.setImportType(importElement.getAttribute("importType"));

		return imp;					
	}


	/**
	 * Converts an XML invoke element to a BPEL Invoke object.
	 */
	protected Activity xml2Invoke(Element invokeElement) {
		Invoke invoke = BPELFactory.eINSTANCE.createInvoke();
		if (invokeElement == null) return invoke;
		
		// Set several parms
		setStandardAttributes(invokeElement, invoke);
		setOperationParms(invokeElement, invoke, null, BPELPackage.eINSTANCE.getInvoke_InputVariable(), BPELPackage.eINSTANCE.getInvoke_OutputVariable(), BPELPackage.eINSTANCE.getPartnerActivity_PartnerLink());

		// Set compensationHandler
		setCompensationHandler(invokeElement, invoke);
		
		// Set the fault handler (for catche-s and catchAll-s)
		FaultHandler faultHandler = xml2FaultHandler(invokeElement);
		if (faultHandler != null && (!faultHandler.getCatch().isEmpty() ||  faultHandler.getCatchAll() != null)) {
			// Only set this on the activity if there is at least one catch clause, or a catchAll clause
			invoke.setFaultHandler(faultHandler);
		}

		// Set the ToPart
		BPELNodeList toPartElements = getBPELChildElementsByLocalName(invokeElement, "toPart");
		Iterator it = toPartElements.iterator();
		while (it.hasNext()) {
			Element toPartElement = (Element)it.next();
			if (BPELUtils.isBPELNamespace(toPartElement.getNamespaceURI())) {
				ToPart toPart = xml2ToPart(toPartElement);
				invoke.getToPart().add(toPart);
			}
		}

		// Set the FromPart
		BPELNodeList fromPartElements = getBPELChildElementsByLocalName(invokeElement, "fromPart");
		it = fromPartElements.iterator();
		while (it.hasNext()) {
			Element fromPartElement = (Element)it.next();
			if (BPELUtils.isBPELNamespace(fromPartElement.getNamespaceURI())) {
				FromPart fromPart = xml2FromPart(fromPartElement);
				invoke.getFromPart().add(fromPart);
			}
		}		
		return invoke;
	}


	/**
	 * Converts an XML reply element to a BPEL Reply object.
	 */
	protected Activity xml2Reply(Element replyElement) {
		Reply reply = BPELFactory.eINSTANCE.createReply();
		if (replyElement == null) return reply;
		
		// Set several parms
		setStandardAttributes(replyElement, reply);
		setOperationParms(replyElement, reply, BPELPackage.eINSTANCE.getReply_Variable(), null, null, BPELPackage.eINSTANCE.getPartnerActivity_PartnerLink());

		if (replyElement.hasAttribute("faultName")) {
			QName qName = BPELUtils.createAttributeValue(replyElement, "faultName");	
			reply.setFaultName(qName);
		}

		// Set the ToPart
		BPELNodeList toPartElements = getBPELChildElementsByLocalName(replyElement, "toPart");
		Iterator it = toPartElements.iterator();
		while (it.hasNext()) {
			Element toPartElement = (Element)it.next();
			if (BPELUtils.isBPELNamespace(toPartElement.getNamespaceURI())) {
				ToPart toPart = xml2ToPart(toPartElement);
				reply.getToPart().add(toPart);
			}
		}

		
		return reply;		
	}
     
     
	/**
	 * Converts an XML receive element to a BPEL Receive object.
	 */
	protected Activity xml2Receive(Element receiveElement) {
		Receive receive = BPELFactory.eINSTANCE.createReceive();
		if (receiveElement == null) return receive;
	
		// Set several parms
		setStandardAttributes(receiveElement, receive);
		setOperationParms(receiveElement, receive, BPELPackage.eINSTANCE.getReceive_Variable(), null, null, BPELPackage.eINSTANCE.getPartnerActivity_PartnerLink());

		// Set createInstance
		if (receiveElement.hasAttribute("createInstance")) {		           
			String createInstance = receiveElement.getAttribute("createInstance");
			receive.setCreateInstance(new Boolean(createInstance.equals("yes")));
		}

		// Set the FromPart
		BPELNodeList fromPartElements = getBPELChildElementsByLocalName(receiveElement, "fromPart");
		Iterator it = fromPartElements.iterator();
		while (it.hasNext()) {
			Element fromPartElement = (Element)it.next();
			if (BPELUtils.isBPELNamespace(fromPartElement.getNamespaceURI())) {
				FromPart fromPart = xml2FromPart(fromPartElement);
				receive.getFromPart().add(fromPart);
			}
		}		
		
		return receive;
	}
	
	protected Correlations xml2Correlations(Element correlationsElement) {
		if (!correlationsElement.getLocalName().equals("correlations"))
			return null;
			
		Correlations correlations = BPELFactory.eINSTANCE.createCorrelations();
		
		// Save all the references to external namespaces		
		saveNamespacePrefix(correlations, correlationsElement);

		BPELNodeList correlationElements = getBPELChildElementsByLocalName(correlationsElement, "correlation");
		for (int i=0; i < correlationElements.getLength(); i++) {
			Element correlationElement = (Element)correlationElements.item(i);
			Correlation correlation = xml2Correlation(correlationElement);
			correlations.getChildren().add(correlation);			
		}
		
		// extensibility elements
		xml2ExtensibleElement(correlations, correlationsElement);
		
		return correlations;
	}		
	
	/**
	 * Converts an XML correlation element to a BPEL Correlation object.
	 */
	protected Correlation xml2Correlation(Element correlationElement) {
    	final Correlation correlation = BPELFactory.eINSTANCE.createCorrelation();

		// Save all the references to external namespaces		
		saveNamespacePrefix(correlation, correlationElement);
    	
		if (correlationElement == null) return correlation;

		// Set set
		if (correlationElement.hasAttribute("set")) {
			final String correlationSetName = correlationElement.getAttribute("set");
			process.getPostLoadRunnables().add(new Runnable() {
				public void run() {	
					CorrelationSet cSet = BPELUtils.getCorrelationSetForActivity(correlation, correlationSetName);
					if (cSet == null) {
						cSet = new CorrelationSetProxy(resource.getURI(), correlationSetName);
					}
					correlation.setSet(cSet);								
				}
			});		
		}

		
		// Set initiation
		Attr initiation = correlationElement.getAttributeNode("initiate");
		if (initiation != null && initiation.getSpecified()) {
			if (initiation.getValue().equals("yes"))
				correlation.setInitiate("yes");
			else if (initiation.getValue().equals("no"))
				correlation.setInitiate("no");
			else if (initiation.getValue().equals("join"))
				correlation.setInitiate("join");
		}
			
		// Set pattern
		Attr pattern = correlationElement.getAttributeNode("pattern");

		if (pattern != null && pattern.getSpecified()) {
			if (pattern.getValue().equals("in"))
				correlation.setPattern(CorrelationPattern.IN_LITERAL);
			else if (pattern.getValue().equals("out"))
					correlation.setPattern(CorrelationPattern.OUT_LITERAL);
				else if (pattern.getValue().equals("out-in"))
					correlation.setPattern(CorrelationPattern.OUTIN_LITERAL);			
		}
		
		xml2ExtensibleElement(correlation, correlationElement);
		
		return correlation;
	}
	
	protected Compensate xml2Compensate(Element compensateElement) {
		final Compensate compensate = BPELFactory.eINSTANCE.createCompensate();
		final Attr scope = compensateElement.getAttributeNode("scope");
		
		if (scope != null && scope.getSpecified()) {
			process.getPostLoadRunnables().add(new Runnable() {
				public void run() {
					compensate.setScope(scope.getValue());
				}
			});
		}

		setStandardAttributes(compensateElement, compensate);
		
		return compensate;
	}
	
	/**
	 * Converts an XML extensiible element to a BPEL extensible element
	 */
	protected void xml2ExtensibleElement(ExtensibleElement extensibleElement, Element element) {
		if (extensionRegistry==null)
			return;
			
		// Get the child nodes, elements and attributes
		List nodes=new ArrayList();
		NodeList nodeList=element.getChildNodes();
		for (int i=0, n=nodeList.getLength(); i<n; i++) {
			if (nodeList.item(i) instanceof Element) {
				final String namespaceURI = ((Element)nodeList.item(i)).getNamespaceURI();
				if (!(BPELUtils.isBPELNamespace(namespaceURI)))
					nodes.add(nodeList.item(i)); 
			}
		}
		
		NamedNodeMap nodeMap=element.getAttributes();
		for (int i=0, n=nodeMap.getLength(); i<n; i++) {
			Attr attr = (Attr)nodeMap.item(i);
			if (attr.getNamespaceURI() != null && !attr.getNamespaceURI().equals(XSDConstants.XMLNS_URI_2000)) {
				nodes.add(attr);	
			}
		}
		
		for (int i=0, n=nodes.size(); i<n; i++) {
			Node node=(Node)nodes.get(i);
			
			// TODO What is this check for? If we're actually checking for
			// the BPEL namespace, use BPELConstants instead.
			if (MessagepropertiesConstants.isMessagePropertiesNamespace(node.getNamespaceURI()))
				continue;
				
			// Handle extensibility element
			if (node.getNodeType()==Node.ELEMENT_NODE) {
					
				// Look if there's an ExtensibilityElement deserializer for this element
				Element childElement=(Element)node;
				QName qname=new QName(childElement.getNamespaceURI(),childElement.getLocalName());
				BPELExtensionDeserializer deserializer=null;
				try {
					deserializer=(BPELExtensionDeserializer)extensionRegistry.queryDeserializer(ExtensibleElement.class,qname);
				} catch (WSDLException e) {}
				if (deserializer!=null) {
					
					// Deserialize the DOM element and add the new Extensibility element to the parent
					// ExtensibleElement
					try {
						Map nsMap = getAllNamespacesForElement(element);
						//ExtensibilityElement extensibilityElement=deserializer.unmarshall(ExtensibleElement.class,qname,childElement,process,nsMap,extensionRegistry,resource.getURI());
						ExtensibilityElement extensibilityElement=deserializer.unmarshall(extensibleElement.getClass(),qname,childElement,process,nsMap,extensionRegistry,resource.getURI());
						extensibleElement.addExtensibilityElement(extensibilityElement);
					} catch (WSDLException e) {
						throw new WrappedException(e);
					}
				}
			} else if (node.getNodeType()==Node.ATTRIBUTE_NODE) {
				// If the attribute is not actually in the file, ignore it.
				// (default attributes added by the schema parser, cause some problems for us)
				if ((node instanceof Attr) && ((Attr)node).getSpecified()) {
					// Handle extensibility attribute
					QName qname=new QName(node.getNamespaceURI(),"extensibilityAttributes");
					BPELExtensionDeserializer deserializer=null;
					try {
						deserializer=(BPELExtensionDeserializer)extensionRegistry.queryDeserializer(ExtensibleElement.class,qname);
					} catch (WSDLException e) {}
					if (deserializer!=null) {
						
						// Create a temp element to host the extensibility attribute
	                    // 
	                    // This turns something that looks like this:
	                    //   <bpws:X someNS:Y="Z"/>
	                    // into something that looks like this:
	                    //   <someNS:extensibilityAttributes xmlns:someNS="http://the.namespace" Y="Z"/>
	                    
						Element tempElement=element.getOwnerDocument().createElementNS(node.getNamespaceURI(), node.getPrefix() + ":extensibilityAttributes");
	                    tempElement.setAttribute(BPELUtils.ATTR_XMLNS + ":" + node.getPrefix(), node.getNamespaceURI());
						tempElement.setAttribute(node.getLocalName(), node.getNodeValue());
						
						// Deserialize the temp DOM element and add the new Extensibility element to the parent
						// ExtensibleElement
						try {
							Map nsMap = getAllNamespacesForElement(element);
							ExtensibilityElement extensibilityElement=deserializer.unmarshall(ExtensibleElement.class,qname,tempElement,process,nsMap,extensionRegistry,resource.getURI());
							if (extensibilityElement!=null)
								extensibleElement.addExtensibilityElement(extensibilityElement);
						} catch (WSDLException e) {
							throw new WrappedException(e);
						}
					}
				}
			}
		}
	}
	
	/**
	 * Helper method to get a string from the given text node or CDATA text node.
	 */
	private String getText(Node node) {
		String data = "";
		boolean containsValidData = false;
		while (node != null) {
			if (node.getNodeType() == Node.TEXT_NODE) {
				Text text = (Text)node;
				data += text.getData();
			} else if (node.getNodeType() == Node.CDATA_SECTION_NODE) {
				data="";
				do {
					CDATASection cdata = (CDATASection) node;
					data += cdata.getData();
					node = node.getNextSibling();
					containsValidData = true;
				} while (node != null && node.getNodeType() == Node.CDATA_SECTION_NODE);
				break;
			}
			node = node.getNextSibling();
		}
		if (!containsValidData) {
			for (int i = 0; i < data.length(); i++) {
				char charData = data.charAt(i);
				if (charData == '\n' || Character.isWhitespace(charData)){}//ignore
				else { //valid data
					containsValidData = true;
					break;
				}
			}
		}
		if (containsValidData) {
			return data;
		} else {
			return null;
		}
	}

	public static Variable getVariable(EObject eObject, String variableName) {
		return VARIABLE_RESOLVER.getVariable(eObject, variableName);
	}	
}
