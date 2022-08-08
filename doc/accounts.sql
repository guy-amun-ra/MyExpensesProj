WITH now as (select cast(strftime('%s', 'now', 'localtime', 'start of day','+1 day','utc') as integer) as now),
amounts AS (select
 amount,
 transfer_peer,
 cr_status,
 date,
 Coalesce(exchange_rate, 1) as exchange_rate,
 Coalesce(
     CASE
         WHEN parent_id
         THEN
                            (SELECT 1.0 *
                            equivalent_amount
                                    / amount
                             FROM   transactions
                             WHERE  _id =
            transactions_with_account.parent_id) *
                                           amount
         ELSE equivalent_amount
     END,
     Coalesce((SELECT
                                   exchange_rate
                                             FROM
                                   account_exchangerates
                                             WHERE
                     account_id =
                     transactions_with_account.account_id
                     AND currency_self =
                         transactions_with_account.currency
                     AND currency_other = 'EUR'), 1) * amount) as equivalent_amount,
 transactions_with_account.account_id from transactions_with_account left join account_exchangerates on transactions_with_account.account_id =
                                                                                      account_exchangerates.account_id
                                                                                      AND currency_self =
                                                                                          transactions_with_account.currency
                                                                                      AND currency_other = 'EUR'
   WHERE parent_id is null AND cr_status != 'VOID'),
   aggregates AS (SELECT
        account_id,
        exchange_rate,
        sum(amount) as total,
        sum(equivalent_amount) as equivalent_total,
        sum(case when amount > 0 AND transfer_peer IS NULL then amount else 0 end) as income,
        sum(case when amount > 0 AND transfer_peer IS NULL then equivalent_amount else 0 end) as equivalent_income,
        sum(case when amount < 0 AND transfer_peer IS NULL then amount else 0 end) as expense,
        sum(case when amount < 0 AND transfer_peer IS NULL then equivalent_amount else 0 end) as equivalent_expense,
        sum(case when transfer_peer is NULL then 0 else amount end) as transfer,
        sum(case when date <= (select now from now) then amount else 0 end ) as current,
        sum(case when date <= (select now from now) then equivalent_amount else 0 end ) as equivalent_current,
        sum(case when cr_status IN ( 'RECONCILED', 'CLEARED' ) then amount else 0 end) as cleared,
        sum(case when cr_status = 'RECONCILED' then amount else 0 end) as reconciled,
        max(case when cr_status = 'CLEARED' then 1 else 0 end) as has_cleared,
        max(date) > (select now from now) as has_future
   from amounts group by account_id)

   SELECT accounts._id                                            AS _id,
          label,
          accounts.description                                    AS description,
          opening_balance,
          accounts.currency                                       AS currency,
          color,
          accounts.grouping                                       AS grouping,
          type,
          sort_key,
          exclude_from_totals,
          sync_account_name,
          uuid,
          sort_direction,
          exchange_rate,
          criterion,
          sealed,
          opening_balance + current as current_balance,
          income AS sum_income,
          expense AS sum_expenses,
          transfer AS sum_transfers,
          opening_balance + total AS total,
          opening_balance + cleared AS cleared_total,
          opening_balance + reconciled AS reconciled_total,
          usages,
          0  AS is_aggregate,
          has_future,
          has_cleared,
          CASE type
            WHEN 'CASH' THEN 0
            WHEN 'BANK' THEN 1
            WHEN 'CCARD' THEN 2
            WHEN 'ASSET' THEN 3
            WHEN 'LIABILITY' THEN 4
            ELSE -1
          end                                                     AS sort_key_type,
          last_used
   FROM  accounts  left join aggregates on _id = account_id
   WHERE  ( hidden = 0 )

   UNION ALL

   SELECT (0 - currency._id) as _id,
      currency as label,
      '' AS description,
      sum(opening_balance) AS opening_balance,
      currency,
      -1 as color,
      currency.grouping,
      'AGGREGATE'                  AS type,
          0                        AS sort_key,
          0                            AS exclude_from_totals,
          NULL                         AS sync_account_name,
          NULL                         AS uuid,
          'DESC'                       AS sort_direction,
          1                            AS exchange_rate,
          0                            AS criterion,
          0                            AS sealed,
          sum(opening_balance) + sum(current)  as current_balance,
      sum(income) as sum_income,
      sum(expense) as sum_expenses,
      sum(transfer) as sum_transfers,
      sum(opening_balance) + sum(total) as total,
      0                            AS cleared_total,
      0                            AS reconciled_total,
      0                            AS usages,
      1                            AS is_aggregate,
      Max(has_future)              AS has_future,
      0                            AS has_cleared,
      0                            AS sort_key_type,
      0                            AS last_used

      from accounts left join aggregates on account_id = accounts._id left join currency on code = currency WHERE exclude_from_totals = 0 group BY currency HAVING Count(*) > 1
   union all
   select
   -2147483648                          AS _id,
       ''                                   AS label,
       ''                                   AS description,
        sum(opening_balance * exchange_rate) AS opening_balance,
        '___'                                AS currency,
       -1                                   AS color,
       'NONE'                               AS grouping,
       'AGGREGATE'                          AS type,
       0                                    AS sort_key,
       0                                    AS exclude_from_totals,
       NULL                                 AS sync_account_name,
       NULL                                 AS uuid,
       'DESC'                               AS sort_direction,
       1                                    AS exchange_rate,
       0                                    AS criterion,
       0                                    AS sealed,
       sum(opening_balance * exchange_rate) + sum(equivalent_current)  as current_balance,
       sum(equivalent_income) as sum_income,
       sum(equivalent_expense) as sum_expenses,
       0                                    AS sum_transfers,
       sum(opening_balance  * exchange_rate) + sum(equivalent_total) as total,
       0                                    AS cleared_total,
              0                                    AS reconciled_total,
              0                                    AS usages,
              2                                    AS is_aggregate,
              Max(has_future)                      AS has_future,
              0                                    AS has_cleared,
              0                                    AS sort_key_type,
              0                                    AS last_used
   from accounts
   left join aggregates on account_id = accounts._id  where exclude_from_totals = 0
   AND (SELECT Count(DISTINCT currency)
         FROM   accounts
         WHERE  currency != 'EUR') > 0
         ORDER  BY is_aggregate,
                   sort_key_type,
                   sort_key,
                   label COLLATE localized;

