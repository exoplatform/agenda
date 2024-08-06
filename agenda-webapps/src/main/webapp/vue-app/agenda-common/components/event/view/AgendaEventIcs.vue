<template>
  <div>
    <div class="d-flex justify-center">
      <v-btn outlined color="primary" class="btn border-radius v-btn v-btn--contained v-size--default v-chip v-chip--outlined theme--light
         primary primary--text mt-4 mb-2 me-5" @click="downloadICS">
        <v-icon size="20" class="uiIcon24x24 pe-2" depressed>
          fa-calendar-plus
        </v-icon>
        {{ $t('agenda.icsbutton') }}
      </v-btn>
    </div>
  </div>
</template>

<script>
export default {
  props: {
    settings: {
      type: Object,
      default: () => null
    },
    event: {
      type: Object,
      default: () => ({})
    },
  },
  methods: {
    async generateICS(event) {
      const formatDate = (date) => {
        return date ? `${new Date(date).toISOString().replace(/[-:]/g, '').split('.')[0]}Z` : '';
      };

      const foldLine = (line) => {
        const maxLength = 70;
        if (line.length <= maxLength) { return line; }
        let result = '';
        while (line.length > maxLength) {
          result += `${line.substring(0, maxLength)}\r\n `;
          line = line.substring(maxLength);
        }
        return result + line;
      };

      const htmlDescription = `${this.$t('agenda.invitationText')} <b>${event.creator.dataEntity.profile.fullname}</b> ${this.$t('agenda.inSpace')} <b>${event.calendar.title}.</b>
      ${event.conferences[0].url ? `<br><b>${this.$t('agenda.visioLink')}</b> <a href="${event.conferences[0].url}">${event.conferences[0].url}</a>` : ''}
      ${event.description ? `<br><br><b>${this.$t('agenda.eventDetail')}</b><br>${event.description}` : ''}
      `.replace(/\\/g, '\\\\').replace(/;/g, '\\;').replace(/,/g, '\\,').replace(/\n/g, '\\n');

      const brandingInformation = await this.$brandingService.getBrandingInformation();
      const icsContent = 'BEGIN:VCALENDAR\r\n' +
        'CALSCALE:GREGORIAN\r\n' +
        'METHOD:PUBLISH\r\n' +
        `PRODID:-//${brandingInformation.siteName}//EN\r\n` +
        'VERSION:2.0\r\n' +
        'BEGIN:VEVENT\r\n' +
        `UID:${event.id || ''}\r\n` +
        `DTSTAMP:${formatDate(new Date())}\r\n` +
        `DTSTART:${formatDate(event.startDate)}\r\n` +
        `DTEND:${formatDate(event.endDate)}\r\n` +
        `SUMMARY:${event.summary || ''}\r\n` +
        `DESCRIPTION:${htmlDescription || ''}\r\n` +
        `X-ALT-DESC;FMTTYPE=text/html:${htmlDescription}\r\n` +
        `LOCATION:${event.location || ''}\r\n` +
        `URL:${event.conferences[0].url || ''}\r\n` +
        `ORGANIZER;CN=${event.creator.dataEntity.profile.fullname}:MAILTO:${event.creator.dataEntity.profile.email}\r\n` +
        'END:VEVENT\r\n' +
        'END:VCALENDAR\r\n';
      return icsContent.split('\r\n').map(foldLine).join('\r\n');
    },
    async downloadICS() {
      const icsContent = await this.generateICS(this.event);

      const blob = new Blob([icsContent], { type: 'text/calendar' });
      const link = document.createElement('a');
      link.href = URL.createObjectURL(blob);
      link.download = `${this.event.summary}.ics`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    },
  }
};
</script>