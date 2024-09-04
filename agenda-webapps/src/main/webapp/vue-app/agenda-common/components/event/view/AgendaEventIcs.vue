<template>
  <div>
    <div v-if="!connectors" class="d-flex justify-center">
      <v-btn outlined color="primary"
        class="pl-2 pr-2 text-body-1 btn border-radius v-chip v-chip--outlined theme--light primary primary--text mt-4 mb-2"
        @click="downloadICS">
        <v-icon class="uiIcon20x20 pe-3">
          fa-calendar-plus
        </v-icon>
        {{ $t('agenda.icsbutton') }}
      </v-btn>
    </div>
    <div v-else @click="downloadICS" :class="{ 'mt-4': connectors, 'ml-n7': connectors }">
      <v-icon class="uiIcon20x20 clickable" depressed>
        fa-calendar-plus
      </v-icon>
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
    connectors: {
      type: Array,
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

      const replaceHtmlTags = (html) => {
        html = html.replace(/<a\s+href="([^"]+)"[^>]*>(.*?)<\/a>/gi, '$2 ($1)');
        html = html.replace(/<\/?[^>]+(>|$)/g, '');
        html = html.replace(/\n{2,}/g, '\n').trim();
        return html;
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

      const foldDescription = (text) => {
        const maxLength = 60;
        const lines = text.split('\n');
        let result = '';
        lines.forEach(line => {
          let currentLine = '';
          const words = line.split(' ');
          words.forEach(word => {
            if (currentLine.length + word.length + 1 > maxLength) {
              result += `${currentLine.trim()}\n`;
              currentLine = `${word} `;
            } else {
              currentLine += `${word} `;
            }
          });

          result += `${currentLine.trim()}\n`;
        });
        return result.trim();
      };

      const confurl = (event.conferences && event.conferences.length > 0) ? event.conferences[0].url : '';
      const htmlDescription = `<html><body>${this.$t('agenda.invitationText')} <b>${event.creator.dataEntity.profile.fullname}</b> ${this.$t('agenda.inSpace')} <b>${event.calendar.title}.</b>
      ${confurl ? `<br><b>${this.$t('agenda.visioLink')}</b> <a href="${confurl}">${confurl}</a>` : ''}
      ${event.description ? `<br><br><b>${this.$t('agenda.eventDetail')}</b><br>${event.description}</body></html>` : ''}
      `.replace(/\\/g, '\\\\').replace(/;/g, '\\;').replace(/,/g, '\\,').replace(/\n/g, '\\n');

      const plainTextEventDescription = event.description ? replaceHtmlTags(event.description) : '';
      const plainTextDescription = `${this.$t('agenda.invitationText')} ${event.creator.dataEntity.profile.fullname} ${this.$t('agenda.inSpace')} ${event.calendar.title}.
      ${confurl ? `${this.$t('agenda.visioLink')} ${confurl}` : ''}
      ${event.description ? `\n${this.$t('agenda.eventDetail')}\n${foldDescription(plainTextEventDescription)}` : ''}
      `.replace(/\\/g, '\\\\').replace(/;/g, '\\;').replace(/,/g, '\\,').replace(/\n/g, '\\n');

      const brandingInformation = await this.$brandingService.getBrandingInformation();
      const icsContent = 'BEGIN:VCALENDAR\r\n' +
        'CALSCALE:GREGORIAN\r\n' +
        'METHOD:PUBLISH\r\n' +
        `PRODID:-//${brandingInformation.siteName}//EN\r\n` +
        'VERSION:2.0\r\n' +
        'BEGIN:VEVENT\r\n' +
        `UID:${event.id || ''}\r\n` +
        `UID:X:${event.id || ''}\r\n` +
        `DTSTAMP:${formatDate(new Date())}\r\n` +
        `DTSTART:${formatDate(event.startDate)}\r\n` +
        `DTEND:${formatDate(event.endDate)}\r\n` +
        `SUMMARY:${event.summary || ''}\r\n` +
        `DESCRIPTION:${plainTextDescription || ''}\r\n` +
        `X-ALT-DESC;FMTTYPE=text/html:${htmlDescription}\r\n` +
        `LOCATION:${event.location || ''}\r\n` +
        `URL:${confurl}\r\n` +
        `ORGANIZER;CN=${event.creator.dataEntity.profile.fullname}:MAILTO:${event.creator.dataEntity.profile.email}\r\n` +
        'END:VEVENT\r\n' +
        'END:VCALENDAR\r\n';

      const processAndFoldText = (text) => {
        let lines = text.split('\n');
        lines = lines.filter(line => !line.startsWith('X-ALT-DESC;FMTTYPE=text/html'));
        const foldedLines = lines.map(line => foldLine(line)).join('\n');
        return foldedLines;
      };
      return processAndFoldText(icsContent);
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
