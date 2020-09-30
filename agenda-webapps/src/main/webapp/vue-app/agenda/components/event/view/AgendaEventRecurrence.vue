<template>
  <div>
    {{ recurrenceLabel }}
    <template v-if="endLabel">
      ( {{ endLabel }} )
    </template>
  </div>
</template>
<script>
export default {
  props: {
    event: {
      type: Object,
      default: null,
    },
  },
  computed: {
    recurrence() {
      return this.event.recurrence || this.event.parent && this.event.parent.recurrence;
    },
    recurrenceFrequency() {
      return this.recurrence && this.recurrence.frequency;
    },
    endLabel() {
      if (this.recurrence) {
        if (this.recurrence.count > 0) {
          return this.$t('agenda.endsAfterEvents', {0 : this.recurrence.count});
        } else if (this.recurrence.until) {
          return this.$t('agenda.endsAt', {0 : this.$dateUtil.formatDateObjectToDisplay(this.$agendaUtils.toDate(this.recurrence.until), {
            year: 'numeric',
            month: 'long',
            day: 'numeric',
          }, eXo.env.portal.language)});
        }
      }
      return null;
    },
    recurrenceLabel() {
      if (!this.recurrenceFrequency) {
        return '';
      }
      switch(this.recurrenceFrequency) {
      case 'DAILY': {
        if (this.recurrence.interval === 1) {
          return this.$t('agenda.daily');
        } else {
          return this.$t('agenda.dailyByInterval', {0 : this.recurrence.interval});
        }
      }
      case 'WEEKLY': {
        if (this.recurrence.interval === 1 && this.recurrence.byDay.length === 1) {
          const day = this.recurrence.byDay[0];
          const dayName = this.$agendaUtils.getDayNameFromDayAbbreviation(day, eXo.env.portal.language);
          return this.$t('agenda.weekly', { 0 : dayName });
        } else if (this.recurrence.interval === 1 && this.recurrence.byDay.length > 1) {
          const dayNames = this.recurrence.byDay.map(day => this.$agendaUtils.getDayNameFromDayAbbreviation(day, eXo.env.portal.language));
          const dayNamesLabel = dayNames.join(' , ').replace(/,([^,]*)$/, `${this.$t('agenda.and')  }$1`);
          return this.$t('agenda.weekly', { 0 : dayNamesLabel });
        } else if (this.recurrence.byDay.length === 1) {
          const day = this.recurrence.byDay[0];
          const dayName = this.$agendaUtils.getDayNameFromDayAbbreviation(day, eXo.env.portal.language);
          return this.$t('agenda.weeklyByInterval', { 0 : this.recurrence.interval, 1: dayName });
        } else {
          const dayNames = this.recurrence.byDay.map(day => this.$agendaUtils.getDayNameFromDayAbbreviation(day, eXo.env.portal.language));
          const dayNamesLabel = dayNames.join(' , ').replace(/,([^,]*)$/, `${this.$t('agenda.and')  }$1`);
          return this.$t('agenda.weeklyByInterval', { 0 : this.recurrence.interval, 1: dayNamesLabel });
        }
      }
      case 'MONTHLY': {
        if (this.recurrence.interval === 1) {
          const dayNumberInMonth = this.recurrence.byMonthDay && this.recurrence.byMonthDay[0];
          return this.$t('agenda.monthly',{ 0 : dayNumberInMonth });
        } else {
          const dayNumberInMonth = this.recurrence.byMonthDay[0];
          return this.$t('agenda.monthlyByInterval',{ 0 : this.recurrence.interval, 1 : dayNumberInMonth });
        }
      }
      case 'YEARLY': {
        if (this.recurrence.interval === 1) {
          const dayNumberInMonth = this.recurrence.byMonthDay && this.recurrence.byMonthDay[0];
          const monthNumber = this.recurrence.byMonth && this.recurrence.byMonth[0];
          const monthName = monthNumber && this.$agendaUtils.getMonthNameFromMonthNumber(monthNumber - 1) || '';
          return this.$t('agenda.yearly',{ 0 : monthName , 1: dayNumberInMonth});
        } else {
          const dayNumberInMonth = this.recurrence.byMonthDay[0];
          const monthNumber = this.recurrence.byMonth[0];
          const monthName = this.$agendaUtils.getMonthNameFromMonthNumber(monthNumber - 1);
          return this.$t('agenda.yearlyByInterval',{ 0: this.recurrence.interval, 1 : monthName , 2: dayNumberInMonth});
        }
      }
      }
      return '';
    },
  },
};
</script>