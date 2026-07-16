const SITE_DATE_FORMATTER = new Intl.DateTimeFormat('en', {
  timeZone: 'Asia/Kuala_Lumpur',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
})

/**
 * Element Plus disabled-date callback: allow the site's current calendar day
 * or earlier, and disable every later day. The candidate uses the calendar
 * date shown by the picker; the reference day uses the same Malaysia timezone
 * as the backend guard.
 */
export function disableFutureDate(candidate, reference = new Date()) {
  if (!(candidate instanceof Date) || Number.isNaN(candidate.getTime())) return false
  if (!(reference instanceof Date) || Number.isNaN(reference.getTime())) return false

  const candidateKey =
    candidate.getFullYear() * 10000 + (candidate.getMonth() + 1) * 100 + candidate.getDate()
  const parts = SITE_DATE_FORMATTER.formatToParts(reference)
  const value = (type) => Number(parts.find((part) => part.type === type)?.value)
  const siteTodayKey = value('year') * 10000 + value('month') * 100 + value('day')

  return candidateKey > siteTodayKey
}
