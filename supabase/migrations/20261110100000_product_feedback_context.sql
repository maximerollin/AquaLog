alter table public.product_feedback
    alter column account_id drop not null,
    add column kind text not null default 'other',
    add column species_id text,
    add column app_version text,
    add column language text,
    add column install_id uuid,
    add constraint product_feedback_kind_check
        check (kind in ('bug', 'translation', 'species', 'idea', 'other')),
    add constraint product_feedback_species_id_check
        check (species_id ~ '^[a-z0-9]+(-[a-z0-9]+)*$' and char_length(species_id) <= 64),
    add constraint product_feedback_app_version_check
        check (char_length(app_version) <= 32),
    add constraint product_feedback_language_check
        check (language ~ '^[A-Za-z]{2,3}(-[A-Za-z0-9]{1,8})*$' and char_length(language) between 2 and 16),
    add constraint product_feedback_author_check
        check (num_nonnulls(account_id, install_id) = 1);

create index product_feedback_install_created_idx
    on public.product_feedback (install_id, created_at)
    where install_id is not null;

drop function public.submit_product_feedback(text);

create function public.submit_product_feedback(
    feedback_message text,
    feedback_kind text default 'other',
    feedback_species_id text default null,
    feedback_app_version text default null,
    feedback_language text default null
)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    caller uuid := auth.uid();
begin
    if caller is null then
        raise exception using errcode = '42501', message = 'authentication required';
    end if;
    if feedback_message is null or char_length(btrim(feedback_message)) not between 1 and 2000 or char_length(feedback_message) > 2000
       or feedback_kind is null or feedback_kind not in ('bug', 'translation', 'species', 'idea', 'other')
       or (feedback_species_id is not null and (feedback_species_id !~ '^[a-z0-9]+(-[a-z0-9]+)*$' or char_length(feedback_species_id) > 64))
       or (feedback_app_version is not null and char_length(feedback_app_version) > 32)
       or (feedback_language is not null and (feedback_language !~ '^[A-Za-z]{2,3}(-[A-Za-z0-9]{1,8})*$' or char_length(feedback_language) not between 2 and 16)) then
        raise exception using errcode = '22023', message = 'invalid feedback';
    end if;
    perform pg_advisory_xact_lock(hashtextextended(caller::text, 0));
    if (select count(*) from public.product_feedback
        where account_id = caller and created_at > clock_timestamp() - interval '1 hour') >= 3 then
        raise exception using errcode = 'P0001', message = 'feedback rate limit reached';
    end if;
    insert into public.product_feedback(account_id, message, kind, species_id, app_version, language)
    values (caller, feedback_message, feedback_kind, feedback_species_id, feedback_app_version, feedback_language);
end;
$$;

revoke all on function public.submit_product_feedback(text, text, text, text, text) from public, anon;
grant execute on function public.submit_product_feedback(text, text, text, text, text) to authenticated;

create function public.submit_anonymous_feedback(
    feedback_install_id uuid,
    feedback_message text,
    feedback_kind text default 'other',
    feedback_species_id text default null,
    feedback_app_version text default null,
    feedback_language text default null
)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    if feedback_install_id is null
       or feedback_message is null or char_length(btrim(feedback_message)) not between 1 and 2000 or char_length(feedback_message) > 2000
       or feedback_kind is null or feedback_kind not in ('bug', 'translation', 'species', 'idea', 'other')
       or (feedback_species_id is not null and (feedback_species_id !~ '^[a-z0-9]+(-[a-z0-9]+)*$' or char_length(feedback_species_id) > 64))
       or (feedback_app_version is not null and char_length(feedback_app_version) > 32)
       or (feedback_language is not null and (feedback_language !~ '^[A-Za-z]{2,3}(-[A-Za-z0-9]{1,8})*$' or char_length(feedback_language) not between 2 and 16)) then
        raise exception using errcode = '22023', message = 'invalid feedback';
    end if;
    perform pg_advisory_xact_lock(hashtextextended(feedback_install_id::text, 0));
    perform pg_advisory_xact_lock(hashtextextended('anonymous-feedback', 0));
    if (select count(*) from public.product_feedback
        where install_id = feedback_install_id and created_at > clock_timestamp() - interval '1 hour') >= 3
       or (select count(*) from public.product_feedback
        where account_id is null and created_at > clock_timestamp() - interval '1 hour') >= 100 then
        raise exception using errcode = 'P0001', message = 'feedback rate limit reached';
    end if;
    insert into public.product_feedback(install_id, message, kind, species_id, app_version, language)
    values (feedback_install_id, feedback_message, feedback_kind, feedback_species_id, feedback_app_version, feedback_language);
end;
$$;

revoke all on function public.submit_anonymous_feedback(uuid, text, text, text, text, text) from public, anon, authenticated;
grant execute on function public.submit_anonymous_feedback(uuid, text, text, text, text, text) to service_role;
