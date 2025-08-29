SELECT 
    p.amount AS SALARY,
    CONCAT(e.first_name, ' ', e.last_name) AS NAME,
    FLOOR(DATEDIFF(CURDATE(), e.dob) / 365) AS AGE,
    d.department_name AS DEPARTMENT_NAME
FROM payments p
JOIN employee e ON p.emp_id = e.emp_id
JOIN department d ON e.department = d.department_id
WHERE DAY(p.payment_time) != 1
ORDER BY p.amount DESC
LIMIT 1;
