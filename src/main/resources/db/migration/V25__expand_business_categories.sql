insert into business_categories (code, name_az, display_order)
select 'FINANCE_BANKING', 'Bank və maliyyə xidmətləri', 70
where not exists (select 1 from business_categories where code = 'FINANCE_BANKING');

insert into business_categories (code, name_az, display_order)
select 'LEGAL_NOTARY', 'Hüquq və notariat', 80
where not exists (select 1 from business_categories where code = 'LEGAL_NOTARY');

insert into business_categories (code, name_az, display_order)
select 'FITNESS_WELLNESS', 'Fitnes və sağlam həyat', 90
where not exists (select 1 from business_categories where code = 'FITNESS_WELLNESS');

insert into business_categories (code, name_az, display_order)
select 'VETERINARY_PET', 'Baytarlıq və heyvanlara qulluq', 100
where not exists (select 1 from business_categories where code = 'VETERINARY_PET');

insert into business_categories (code, name_az, display_order)
select 'AUTOMOTIVE', 'Avtomobil xidmətləri', 110
where not exists (select 1 from business_categories where code = 'AUTOMOTIVE');

insert into business_categories (code, name_az, display_order)
select 'REAL_ESTATE', 'Daşınmaz əmlak', 120
where not exists (select 1 from business_categories where code = 'REAL_ESTATE');

insert into business_categories (code, name_az, display_order)
select 'EVENTS_PHOTOGRAPHY', 'Tədbir və foto xidmətləri', 130
where not exists (select 1 from business_categories where code = 'EVENTS_PHOTOGRAPHY');

insert into business_categories (code, name_az, display_order)
select 'CUSTOMER_SERVICE', 'Müştəri xidmətləri və qəbul mərkəzləri', 140
where not exists (select 1 from business_categories where code = 'CUSTOMER_SERVICE');

insert into business_categories (code, name_az, display_order)
select 'HOSPITALITY_FOOD', 'Qonaqpərvərlik və iaşə', 150
where not exists (select 1 from business_categories where code = 'HOSPITALITY_FOOD');
