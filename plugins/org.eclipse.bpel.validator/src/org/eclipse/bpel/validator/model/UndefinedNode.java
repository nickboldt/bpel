/*******************************************************************************
 * Copyright (c) 2006 Oracle Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Oracle Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.bpel.validator.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;


/**
 * @author Michal Chmielewski (michal.chmielewski@oracle.com)
 * @date Dec 14, 2006
 *
 */
public class UndefinedNode implements INode {
	
	HashMap<String,String> mMap = new HashMap<String,String>(5);
	String fNodeName ;
	
	/**
	 * @param name
	 * @param args
	 */
	public UndefinedNode ( String name, String ... args ) {
		fNodeName = name;
		for(int i=0; i < args.length; i += 2) {
			mMap.put(args[i], args[i+1]);			
		}
	}
	
	/** (non-Javadoc)
	 * @see org.eclipse.bpel.validator.model.INode#children()
	 */
	@SuppressWarnings("unchecked")
	public List<INode> children() {
		return Collections.EMPTY_LIST;
	}

	/** (non-Javadoc)
	 * @see org.eclipse.bpel.validator.model.INode#getAttribute(java.lang.String)
	 */
	public String getAttribute(String name) {
		return mMap.get(name);
	}

	/** (non-Javadoc)
	 * @see org.eclipse.bpel.validator.model.INode#getNode(java.lang.String)
	 */
	
	public INode getNode (String name) {		
		return null;
	}

	/** (non-Javadoc)
	 * @see org.eclipse.bpel.validator.model.INode#getNodeList(java.lang.String)
	 */
	@SuppressWarnings("unchecked")
	public List<INode> getNodeList(String name) {
		return Collections.EMPTY_LIST;
	}

	/** (non-Javadoc)
	 * @see org.eclipse.bpel.validator.model.INode#isResolved()
	 */
	public boolean isResolved() {
		return false;		
	}

	/** (non-Javadoc)
	 * @see org.eclipse.bpel.validator.model.INode#nodeName()
	 */
	
	public String nodeName() {
		return fNodeName;
	}

	/** (non-Javadoc)
	 * @see org.eclipse.bpel.validator.model.INode#nodeValidator()
	 */
	public Validator nodeValidator() { 
		return null;
	}

	/** (non-Javadoc)
	 * @see org.eclipse.bpel.validator.model.INode#nodeValue()
	 */
	
	public Object nodeValue() {	
		return null;
	}

	/** (non-Javadoc)
	 * @see org.eclipse.bpel.validator.model.INode#parentNode()
	 */
	public INode parentNode() {
		return null;
	}

	/** (non-Javadoc)
	 * @see org.eclipse.bpel.validator.model.INode#rootNode()
	 */
	public INode rootNode() {		
		return null;
	}
}