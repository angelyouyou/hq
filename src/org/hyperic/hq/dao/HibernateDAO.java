/*                                                                 
 * NOTE: This copyright does *not* cover user programs that use HQ 
 * program services by normal system calls through the application 
 * program interfaces provided as part of the Hyperic Plug-in Development 
 * Kit or the Hyperic Client Development Kit - this is merely considered 
 * normal use of the program, and does *not* fall under the heading of 
 * "derived work". 
 *  
 * Copyright (C) [2004, 2005, 2006], Hyperic, Inc. 
 * This file is part of HQ.         
 *  
 * HQ is free software; you can redistribute it and/or modify 
 * it under the terms version 2 of the GNU General Public License as 
 * published by the Free Software Foundation. This program is distributed 
 * in the hope that it will be useful, but WITHOUT ANY WARRANTY; without 
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
 * PARTICULAR PURPOSE. See the GNU General Public License for more 
 * details. 
 *                
 * You should have received a copy of the GNU General Public License 
 * along with this program; if not, write to the Free Software 
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 
 * USA. 
 */

package org.hyperic.hq.dao;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import org.hibernate.LockMode;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.type.Type;
import org.hibernate.criterion.Example;
import org.hyperic.dao.DAOFactory;
import org.hyperic.hibernate.PersistedObject;
import org.hyperic.hq.authz.shared.PermissionException;
import org.hyperic.util.pager.PageControl;
import org.hyperic.util.pager.PageList;

/**
 * Hibernate Data Access Object
 * The actual DAO is subclass of this object.
 * This class should actually be implemented with J2SE 5 Generics,
 * but we have to support JDK 1.4, :(
 */
public abstract class HibernateDAO {
    private Class      _persistentClass;
    private DAOFactory _daoFactory;

    protected HibernateDAO(Class persistentClass, DAOFactory f) {
        _persistentClass = persistentClass;
        _daoFactory      = f;
    }

    public Class getPersistentClass() {
        return _persistentClass;
    }

    public Session getSession() {
        return _daoFactory.getCurrentSession();
    }

    protected Object findById(Serializable id) {
        return findById(id, false);
    }

    protected Object get(Serializable id) {
        return getSession().get(getPersistentClass(), id);
    }

    protected Object findById(Serializable id, boolean lock) {
        return lock
               ? getSession().load(getPersistentClass(), id, LockMode.UPGRADE)
               : getSession().load(getPersistentClass(), id);
    }

    /**
     * Simple pojo attribute value search, uses Hibernate Example Criteria
     * search.
     *
     * Instantiate pojo, set the pojo attribute values, then add corresponding
     * hdm proerty names with calls to Persisted.addSearchable().
     * 
     * @param p
     * @return
     */
    public List findByExample(final PersistedObject p) {
        // searchable properties
        Example.PropertySelector selector =
                new Example.PropertySelector() {
                    public boolean include(Object val, String name, Type type)
                    {
                        return p.getSearchable().contains(name);
                    }
                };
        
        return getSession().createCriteria(getPersistentClass())
                .add(Example.create(p).setPropertySelector(selector))
                .list();
    }

    public Collection findAll() {
        return getSession().createCriteria(getPersistentClass()).list();
    }

    public int size() {
        return ((Integer)getSession()
            .createQuery("select count(*) from "+getPersistentClass().getName())
            .uniqueResult())
            .intValue();
    }

    public int size(Collection coll) {
        return ((Integer)getSession()
            .createFilter(coll, "select count(*)")
            .uniqueResult())
            .intValue();
    }

    protected void evict(Object entity) {
        getSession().evict(entity);
    }

    protected Object merge(Object entity) {
        return getSession().merge(entity);
    }

    protected void save(Object entity) {
        getSession().saveOrUpdate(entity);
    }

    protected void update(Object entity) {
        getSession().update(entity);
    }

    protected void remove(Object entity) {
        getSession().delete(entity);
    }

    public PersistedObject findPersistedList(PersistedObject entity) {
        throw new UnsupportedOperationException("findPersistedList not " +
                                                "supported");
    }

    public PersistedObject findPersistedById(Integer subjectId, Serializable id)
        throws PermissionException
    {
        throw new UnsupportedOperationException("findPersistedById " +
                                                "not supported");
    }

    public PersistedObject findPersisted(PersistedObject entity)
        throws PermissionException
    {
        throw new UnsupportedOperationException("findPersisted not supported");
    }

    public void savePersisted(PersistedObject entity) throws PermissionException
    {
        throw new UnsupportedOperationException("savePersisted not supported");
    }
    
    public void removePersisted(PersistedObject entity)
        throws PermissionException
    {
        throw new UnsupportedOperationException("removePersisted not " +
                                                "supported");
    }

    protected PageList getPagedResults(Query q, int total, PageControl pc) {
        if (pc.getPagesize() != PageControl.SIZE_UNLIMITED) {
            q.setMaxResults(pc.getPagesize());
        }
        
        if (pc.getPageEntityIndex() != 0) {
            q.setFirstResult(pc.getPageEntityIndex());
        }
        
        return new PageList(q.list(), total);
    }
}
