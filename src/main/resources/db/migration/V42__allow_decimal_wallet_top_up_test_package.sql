-- Keep AZN_3 as a low-value live Epoint smoke test package.
alter table wallet_top_up_requests drop constraint if exists chk_wallet_top_up_requests_fixed_package;
alter table wallet_top_up_packages drop constraint if exists chk_wallet_top_up_packages_fixed_catalog;
alter table wallet_top_up_packages drop constraint if exists chk_wallet_top_up_packages_coin_amount;
alter table wallet_top_up_packages drop constraint if exists chk_wallet_top_up_packages_amount;

alter table wallet_top_up_packages
    alter column amount_azn type numeric(10, 2);

alter table wallet_top_up_requests
    alter column amount_azn type numeric(10, 2);

update wallet_top_up_packages
set amount_azn = 0.10,
    coin_amount = 1
where code = 'AZN_3';

alter table wallet_top_up_packages add constraint chk_wallet_top_up_packages_amount check (
    amount_azn in (0.10, 5.00, 10.00, 15.00, 20.00)
);

alter table wallet_top_up_packages add constraint chk_wallet_top_up_packages_fixed_catalog check (
    (code = 'AZN_3' and amount_azn = 0.10 and coin_amount = 1
        and payment_url = 'https://cb.birbank.business/pay/7847238243e34c9c9dd4666f749d5879'
        and display_order = 1)
    or (code = 'AZN_5' and amount_azn = 5.00 and coin_amount = 50
        and payment_url = 'https://cb.birbank.business/pay/89a5510f409341ecbf82badf75512d21'
        and display_order = 2)
    or (code = 'AZN_10' and amount_azn = 10.00 and coin_amount = 100
        and payment_url = 'https://cb.birbank.business/pay/75c998cbda8e4674bb11cbf961d91c27'
        and display_order = 3)
    or (code = 'AZN_15' and amount_azn = 15.00 and coin_amount = 150
        and payment_url = 'https://cb.birbank.business/pay/1b0fcb5e4e5f4b23a4ed8ec1b8fbc978'
        and display_order = 4)
    or (code = 'AZN_20' and amount_azn = 20.00 and coin_amount = 200
        and payment_url = 'https://cb.birbank.business/pay/d2000eed920c4af68dc83106e6b56b9f'
        and display_order = 5)
);

alter table wallet_top_up_requests add constraint chk_wallet_top_up_requests_fixed_package check (
    (package_code = 'AZN_3' and amount_azn = 3.00 and coin_amount = 30)
    or
    (
        payment_provider <> 'manual'
        and (
            (package_code = 'AZN_3' and amount_azn = 0.10 and coin_amount = 1)
            or (package_code = 'AZN_5' and amount_azn = 5.00 and coin_amount = 50)
            or (package_code = 'AZN_10' and amount_azn = 10.00 and coin_amount = 100)
            or (package_code = 'AZN_15' and amount_azn = 15.00 and coin_amount = 150)
            or (package_code = 'AZN_20' and amount_azn = 20.00 and coin_amount = 200)
        )
    )
    or (
        payment_provider = 'manual'
        and (
            (package_code = 'AZN_3' and (
                (amount_azn = 0.10 and coin_amount = 1)
                or (amount_azn = 3.00 and coin_amount = 30)
            ) and payment_url = 'https://cb.birbank.business/pay/7847238243e34c9c9dd4666f749d5879')
            or (package_code = 'AZN_5' and amount_azn = 5.00 and coin_amount = 50
                and payment_url = 'https://cb.birbank.business/pay/89a5510f409341ecbf82badf75512d21')
            or (package_code = 'AZN_10' and amount_azn = 10.00 and coin_amount = 100
                and payment_url = 'https://cb.birbank.business/pay/75c998cbda8e4674bb11cbf961d91c27')
            or (package_code = 'AZN_15' and amount_azn = 15.00 and coin_amount = 150
                and payment_url = 'https://cb.birbank.business/pay/1b0fcb5e4e5f4b23a4ed8ec1b8fbc978')
            or (package_code = 'AZN_20' and amount_azn = 20.00 and coin_amount = 200
                and payment_url = 'https://cb.birbank.business/pay/d2000eed920c4af68dc83106e6b56b9f')
        )
    )
);
