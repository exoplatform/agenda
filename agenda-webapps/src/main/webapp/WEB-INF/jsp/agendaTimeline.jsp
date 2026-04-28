<%
/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
%>
<%@taglib uri="http://java.sun.com/portlet_2_0" prefix="portlet" %>
<portlet:defineObjects />
<portlet:actionURL var="saveSettingsUrl" />

<%
  String portletId = (String) request.getAttribute("portletStorageId");
  String domId = "AgendaTimelineApplication" + portletId;
  boolean canEdit = (boolean) request.getAttribute("canEdit");
  Object settings = (String[]) request.getAttribute("settings");
  if (settings != null) {
    settings = ((String[]) settings)[0];
  }
%>


<div class="VuetifyApp">
  <div id="<%=domId%>">
    <script type="text/javascript">
      require(['PORTLET/agenda/AgendaTimeline'], app => app.init('<%=domId%>', <%=canEdit%>, <%=settings%>, '<%=saveSettingsUrl%>'));
    </script>
  </div>
</div>