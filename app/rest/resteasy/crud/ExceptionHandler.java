/*
    This file is part of resteasy-crud-play-module.
    
    Copyright Lunatech Research 2010

    resteasy-crud-play-module is free software: you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    resteasy-crud-play-module is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU General Lesser Public License
    along with resteasy-crud-play-module.  If not, see <http://www.gnu.org/licenses/>.
*/
package rest.resteasy.crud;

import java.net.HttpURLConnection;
import java.util.List;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Variant;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.hibernate.PropertyValueException;
import org.hibernate.validator.InvalidStateException;
import org.hibernate.validator.InvalidValue;
import org.jboss.resteasy.spi.Failure;

import play.Logger;
import play.modules.resteasy.crud.InvalidResponse;

/**
 * Maps RESTEasy failures to responses, finds Hibernate Validation exceptions
 * and maps them to InvalidResponse and tries to unwrap exceptions generally.
 * @author Stéphane Épardaud <stef@epardaud.fr>
 */
@Provider
public class ExceptionHandler implements ExceptionMapper<Throwable> {

	@Context
	private Request request;

	public Response toResponse(Throwable t) {
		Logger.debug("Exception mapper called");
		InvalidValue[] invalidValues = findInvalidValues(t);
		if (invalidValues != null) {
			InvalidResponse r = new InvalidResponse();
			if (invalidValues != null) {
				for (InvalidValue val : invalidValues) {
					r.addError(val.getPropertyName(), val.getMessage());
				}
			}
			List<Variant> variants = Variant.mediaTypes(
					MediaType.APPLICATION_JSON_TYPE,
					MediaType.APPLICATION_XML_TYPE,
					MediaType.valueOf("application/*+xml"),
					MediaType.TEXT_XML_TYPE).build();
			Variant variant = request.selectVariant(variants);
			return Response.status(HttpURLConnection.HTTP_BAD_REQUEST)
					.entity(r).variant(variant).build();
		}
		// also handle "normal" JAX-RS exceptions
		WebApplicationException wx = findWebApplicationException(t);
		if (wx != null) {
			return wx.getResponse();
		}
		// handle security checks as well
		/* FIXME: play port: fix auth
		if (t instanceof AuthorizationException) {
			return Response.status(HttpURLConnection.HTTP_FORBIDDEN).entity(
					t.getMessage()).build();
		}
		*/
		// and RESTEasy failures (grrr)
		if (t instanceof Failure) {
			Failure f = (Failure) t;
			if (f.getResponse() != null)
				return f.getResponse();
			if (f.getErrorCode() != -1)
				return Response.status(f.getErrorCode()).entity(f.getMessage())
						.build();
			// fall through
		}
		Logger.error(t, "Uncaught exception");
		return Response.serverError().build();
	}

	private WebApplicationException findWebApplicationException(Throwable t) {
		if (t instanceof WebApplicationException)
			return (WebApplicationException) t;
		if (t.getCause() != null)
			return findWebApplicationException(t.getCause());
		return null;
	}

	private InvalidValue[] findInvalidValues(Throwable t) {
		if (t instanceof InvalidStateException)
			return ((InvalidStateException) t).getInvalidValues();
		if (t instanceof PropertyValueException) {
			PropertyValueException x = (PropertyValueException) t;
			// watch it, keep this in sync with exception handling
			InvalidValue invalidValue = new InvalidValue(x.getMessage(), null,
					x.getPropertyName(), null, null);
			InvalidValue[] invalidValues = new InvalidValue[1];
			invalidValues[0] = invalidValue;
			return invalidValues;
		}
		if (t.getCause() != null)
			return findInvalidValues(t.getCause());
		return null;
	}

}
