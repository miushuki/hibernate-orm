/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2015, Red Hat Inc. or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat Inc.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.hibernate.test.type.converter;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import javax.persistence.AttributeConverter;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import org.hibernate.Session;
import org.hibernate.jdbc.Work;

import org.hibernate.testing.TestForIssue;
import org.hibernate.testing.junit4.BaseCoreFunctionalTestCase;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Steve Ebersole
 */
public class NullHandlingTests extends BaseCoreFunctionalTestCase {
	@Override
	protected Class<?>[] getAnnotatedClasses() {
		return new Class[] { TheEntity.class };
	}

	@Test
	@TestForIssue( jiraKey = "HHH-8697" )
	public void testNullReplacementOnBinding() {
		TheEntity theEntity = new TheEntity( 1 );

		Session session = openSession();
		session.beginTransaction();
		// at this point TheEntity.sex is null
		// lets make sure that the converter is given a chance to adjust that to UNKNOWN...
		session.save( theEntity );
		session.getTransaction().commit();
		session.close();

		session = openSession();
		session.beginTransaction();
		session.doWork(
				new Work() {
					@Override
					public void execute(Connection connection) throws SQLException {
						ResultSet rs = connection.createStatement().executeQuery( "select sex from the_entity where id=1" );
						try {
							if ( !rs.next() ) {
								throw new RuntimeException( "Could not locate inserted row" );
							}

							String sexDbValue = rs.getString( 1 );

							if ( rs.next() ) {
								throw new RuntimeException( "Found more than one row" );
							}

							assertEquals( Sex.UNKNOWN.name().toLowerCase( Locale.ENGLISH ), sexDbValue );
						}
						finally {
							rs.close();
						}
					}
				}
		);
		session.getTransaction().commit();
		session.close();

		session = openSession();
		session.beginTransaction();
		session.delete( theEntity );
		session.getTransaction().commit();
		session.close();
	}

	@Test
	@TestForIssue( jiraKey = "HHH-9320" )
	public void testNullReplacementOnExtraction() {
		Session session = openSession();
		session.beginTransaction();
		session.doWork(
				new Work() {
					@Override
					public void execute(Connection connection) throws SQLException {
						connection.createStatement().execute( "insert into the_entity(id, sex) values (1, null)" );
					}
				}
		);
		session.getTransaction().commit();
		session.close();

		session = openSession();
		session.beginTransaction();
		// at this point TheEntity.sex is null in the database
		// lets load it and make sure that the converter is given a chance to adjust that to UNKNOWN...
		TheEntity theEntity = (TheEntity) session.get( TheEntity.class, 1 );
		session.getTransaction().commit();
		session.close();

		assertEquals( Sex.UNKNOWN, theEntity.sex );

		session = openSession();
		session.beginTransaction();
		session.delete( theEntity );
		session.getTransaction().commit();
		session.close();
	}

	@Entity( name = "TheEntity" )
	@Table( name = "the_entity" )
	public static class TheEntity {
		@Id
		public Integer id;

		@Convert( converter = SexConverter.class )
		public Sex sex;

		public TheEntity() {
		}

		public TheEntity(Integer id) {
			this.id = id;
		}
	}

	public static enum Sex {
		MALE,
		FEMALE,
		UNKNOWN
	}

	public static class SexConverter implements AttributeConverter<Sex,String> {
		@Override
		public String convertToDatabaseColumn(Sex attribute) {
			// HHH-8697
			if ( attribute == null ) {
				return Sex.UNKNOWN.name().toLowerCase( Locale.ENGLISH );
			}

			return attribute.name().toLowerCase( Locale.ENGLISH );
		}

		@Override
		public Sex convertToEntityAttribute(String dbData) {
			// HHH-9320
			if ( dbData == null ) {
				return Sex.UNKNOWN;
			}

			return Sex.valueOf( dbData.toUpperCase( Locale.ENGLISH ) );
		}
	}
}
